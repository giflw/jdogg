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

package nl.weeaboo.ogg;

import java.io.IOException;
import java.io.InputStream;

public class BasicOggInput implements OggInput {

	private InputStream in;
	
	public BasicOggInput(InputStream in) {
		this.in = in;
	}
	
	//Functions
	@Override
	public void close() throws IOException {
		in.close();
	}

	@Override
	public InputStream openStream() throws IOException {
		return new InputStreamView(in, length());
	}
	
	@Override
	public long length() throws IOException {
		return -1;
	}
	
	//Getters
	
	//Setters
	
}
