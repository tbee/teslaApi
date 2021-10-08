package org.tbee.tesla.login.javafx;

/*-
 * #%L
 * TeslaAPIJavafxLogin
 * %%
 * Copyright (C) 2020 - 2021 Tom Eugelink
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleProxyServer {

	/**
	 * 
	 */
	public void runServer(String host, int remoteport, int localport) {
		String prefix = host + ":"  + remoteport + ": ";
		System.out.println(prefix + " Starting proxy for " + host + ":" + remoteport + " on port " + localport);
		
		// local -> remote
		Thread mainThread = new Thread(() -> {
			final AtomicBoolean abort = new AtomicBoolean(false);
			Thread threadFromLocalToRemote = Thread.currentThread();
			threadFromLocalToRemote.setName("LocalToRemote");
			Thread threadFromRemoteToLocal = null;
			
			try (
				// Create a ServerSocket to listen for connections with
				ServerSocket serverSocket = new ServerSocket(localport);
			){
				// restart if connection is closed
				while (true) {
					
					System.out.println(prefix + "waiting");								
					try (
						// Wait for a connection on the local port
						final Socket socketToLocal = serverSocket.accept();
						final InputStream inputStreamFromLocal = socketToLocal.getInputStream();
						final OutputStream outputStreamToLocal = socketToLocal.getOutputStream();
							
						// Make a connection to the remote
						final Socket socketToRemote = new Socket(host, remoteport);
						final InputStream inputStreamFromRemote = socketToRemote.getInputStream();
						final OutputStream outputSteamToRemote = socketToRemote.getOutputStream();
					) {			
						// remote -> local
						threadFromRemoteToLocal = new Thread(() -> {
							copy(prefix + "remote -> local: ", inputStreamFromRemote, outputStreamToLocal, abort, threadFromLocalToRemote, socketToLocal);
						});
						threadFromRemoteToLocal.setName("RemoteToLocal");
						threadFromRemoteToLocal.start();
						
						// local -> remote
						copy(prefix + "local -> remote: ", inputStreamFromLocal, outputSteamToRemote, abort, threadFromRemoteToLocal, socketToRemote);
					}
					catch (IOException e) {
						e.printStackTrace();
					}
					finally {
						System.out.println(prefix + "closing both sockets");								
					}
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		});
		mainThread.start();
	}
	
	private void copy(String prefix2, InputStream inputStream, OutputStream outputSteam, AtomicBoolean abort, Thread otherThread, Socket otherSocket) {
		final byte[] request = new byte[1024];
		int bytesRead;
		try {
			System.out.println(prefix2 + "waiting");
			while ((bytesRead = inputStream.read(request)) != -1) {
				outputSteam.write(request, 0, bytesRead);
				System.out.println(prefix2 + bytesRead + "\n======================\n" + new String(request, 0, bytesRead) + "\n======================");
				outputSteam.flush();
				if (abort.get()) {
					System.out.println(prefix2  + "abort is set, aborting");								
					return;
				}
				System.out.println(prefix2 + "waiting");
			}
			System.out.println(prefix2 + bytesRead);
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			System.out.println(prefix2 + "done");								
			abort.set(true);
			try {
				otherSocket.close();
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
			otherThread.interrupt();
			System.out.println(prefix2 + "other thread interupted / other socket closed: " + otherThread.getName());								
		}
	}
}
