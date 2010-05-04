/* JDOgg
 * 
 * Copyright (c) 2010 Timon Bijlsma
 *   
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
   
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 * 
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package nl.weeaboo.jdogg.test;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import nl.weeaboo.jdogg.GreedyStreamSelector;
import nl.weeaboo.jdogg.OggDecoder;
import nl.weeaboo.jdogg.OggStreamHandler;
import nl.weeaboo.jdogg.kate.KateDecoder;
import nl.weeaboo.jdogg.kate.KateEvent;
import nl.weeaboo.jdogg.kate.KateRendererElement;
import nl.weeaboo.jdogg.kate.KateRendererState;
import nl.weeaboo.jdogg.player.AudioSink;
import nl.weeaboo.jdogg.player.VideoWindow;
import nl.weeaboo.jdogg.player.Player;
import nl.weeaboo.jdogg.theora.TheoraDecoder;
import nl.weeaboo.jdogg.theora.VideoFormat;
import nl.weeaboo.jdogg.theora.VideoFrame;
import nl.weeaboo.jdogg.vorbis.VorbisDecoder;

/*
 * TODO: Seeking kills the subtitles for several seconds, subtitle chunks are
 * rare and several seconds may pass before we find a new chunk. Perhaps it's
 * possible to prescan the entire file and store all subtitle chunks for the
 * entire files.
 * 
 * TODO: Create a pool of Packet objects, allow code to explicitly give objects
 * back to the pool.
 * 
 * TODO: What to do about sequential streams? Should I reuse the streamhandlers
 * for streams that have just ended?
 * 
 * TODO: Once we reach the end of the file, seeking/pause/everything breaks.
 */

public class OggTest {

	//Functions
	public static void main(String args[]) throws IOException {
		try {
		    for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
		        if ("Nimbus".equals(info.getName())) {
		            UIManager.setLookAndFeel(info.getClassName());
		            break;
		        }
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
			
		Player player = new Player();
		player.setInput(new File(args[0]));
		player.start();
		
		//playVideo(new File(args[0]));
		//playVorbis(new File(args[0]));
	}
	
	protected static void playVideo(File file) throws IOException {
		OggDecoder decoder = new OggDecoder();
		decoder.setInput(file);
		
		//Scan through entire file once
		if (decoder.isSeekable()) {
			while (!decoder.isEOF()) {
				decoder.update();
			}			
			decoder.seekFrac(0);
		}
		
		TheoraDecoder theoraDecoder = new TheoraDecoder();
		VorbisDecoder vorbisDecoder = new VorbisDecoder();
		KateDecoder kateDecoder = new KateDecoder();
		
		OggStreamHandler streamHandlers[] = new OggStreamHandler[] {
				theoraDecoder, vorbisDecoder, kateDecoder
		};
		decoder.readStreamGroup(new GreedyStreamSelector(streamHandlers));
		
		//Setup sinks
		decoder.readHeaders();
		
		VideoWindow window = new VideoWindow("Theora/Vorbis/Kate Test Window");
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		final AudioSink audioSink = new AudioSink(vorbisDecoder.getAudioFormat());
		try {
			audioSink.start();
		} catch (LineUnavailableException lue) {
			lue.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				audioSink.flush();
			}
		}, 1000, 10);
		
		KateRendererState krs = new KateRendererState();
		
		boolean limitSpeed = true;
		double audioSyncFudge = 0.1;
		double audioSyncFactor = 10;
		
		double targetTime = 0;
		long lastTime = System.nanoTime();		
		while (true) {						
			//Process Theora
			VideoFrame frame = null;
			while (targetTime < 0 || theoraDecoder.getTime() <= targetTime) {
				while (!decoder.isEOF() && theoraDecoder.isBufferEmpty()) {
					decoder.update();
				}
				
				if (targetTime >= 0) {
					double ttime = theoraDecoder.getTime();
					if (ttime >= targetTime) {
						break;
					}
					
					VideoFormat fmt = theoraDecoder.getVideoFormat();
					if (ttime >= 0 && ttime + fmt.getFrameDuration() < targetTime) {
						theoraDecoder.skip();
					} else {
						frame = theoraDecoder.read();
					}
				} else {
					frame = theoraDecoder.read();
					break;
				}
			}
			
			//Update video sink
			if (frame != null) {
				frame.readPixels(window.getVideoPanel());
			}
			
			//Process Vorbis
			while (vorbisDecoder.getTime() < 0.5 + Math.max(targetTime, theoraDecoder.getTime())) {
				while (!decoder.isEOF() && vorbisDecoder.isBufferEmpty()) {
					decoder.update();
				}
				
				byte b[] = vorbisDecoder.read();
				audioSink.buffer(b, 0, b.length);
			}

			//Process Kate
			while (!kateDecoder.isBufferEmpty()) {
				KateEvent event = kateDecoder.peek();
				if (event == null || event.getStartTime() >= decoder.getTime()) {
					break;
				}
				
				event = kateDecoder.read();
				krs.addElement(new KateRendererElement(event));
			}
					
			//Update GUI
			if (decoder.getTime() >= 0) {
				window.setPosition(decoder.getTime(), decoder.getEndTime());
			}

			//Update subtitle sink
			krs.update(decoder.getTime());
			window.setSubtitles(krs.getText(2));
						
			//Limit Speed
			long currentTime = System.nanoTime();
			if (targetTime >= 0) {
				double dt = (currentTime - lastTime) / 1000000000.0;
				double audioTime = vorbisDecoder.getTime() - audioSink.getBufferDuration();
				if (audioTime >= 0 && Math.abs(targetTime - audioTime) > audioSyncFudge) {
					double a = audioSyncFactor;
					double diff = (audioTime - targetTime);					
					targetTime += diff * Math.min(1.0, a * dt);
					
					//System.out.printf("%.2f %.2f %d\n", targetTime, audioTime, audioSink.getBufferLength());
				}
				targetTime += dt;
			}
			lastTime = currentTime;
			
			if (targetTime >= 0) {
				double wait = decoder.getTime() - targetTime;					
				if (limitSpeed && wait > 0) {
					try {
						Thread.sleep(Math.round(wait * 500.0));
					} catch (InterruptedException e) { }
				}
			} else {
				//We're lost, find some timestamp to hang on to :)
				targetTime = decoder.getTime();
			}
		}		
	}
	
	protected static void playVorbis(File file) throws IOException {
		boolean limitSpeed = false;
		
		final VorbisDecoder vorbisDecoder = new VorbisDecoder();
		
		OggDecoder decoder = new OggDecoder();
		decoder.setInput(file);		
		decoder.readStreamGroup(new GreedyStreamSelector(vorbisDecoder));
		
		long t0 = System.nanoTime();
		while (!decoder.isEOF()) {
			decoder.update();
			
			while (!vorbisDecoder.isBufferEmpty()) {
				vorbisDecoder.read();
			}

			if (limitSpeed) {
				double time = (System.nanoTime() - t0) / 1000000000.0;
				double wait = vorbisDecoder.getTime() - time;
				
				System.out.printf("time=%.2f audio=%.2f\n", time, vorbisDecoder.getTime());
	
				if (wait > 0) {
					try {
						Thread.sleep(Math.round(wait * 1000.0));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}		
	}
	
	//Getters
	
	//Setters
}
