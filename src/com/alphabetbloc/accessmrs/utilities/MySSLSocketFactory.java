/*
 * Copyright 2012 Timelappse
 * Copyright 2012 Andlytics Project
 * Copyright 2012 Louis Fazen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.alphabetbloc.accessmrs.utilities;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.util.Log;

/**
 * 
 * @author Nikolay Nelenkov (? https://github.com/nelenkov/custom-cert-https I think... though license is different author?
 * "loosely based on org.apache.http.conn.ssl.SSLSocketFactory")
 * @author Louis Fazen (louis.fazen@gmail.com)
 */
public class MySSLSocketFactory implements LayeredSocketFactory {

	private static final String TAG = MySSLSocketFactory.class.getSimpleName();
	// private SSLContext sslCtx;
	private SSLSocketFactory socketFactory;
	private X509HostnameVerifier hostnameVerifier;

	public MySSLSocketFactory(SSLContext sslCtx, X509HostnameVerifier hostnameVerifier) {
		// this.sslCtx = sslCtx;
		this.socketFactory = sslCtx.getSocketFactory();
		this.hostnameVerifier = hostnameVerifier;

	}

	@Override
	public Socket connectSocket(Socket sock, String host, int port, InetAddress localAddress, int localPort, HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
		if (host == null) {
			throw new IllegalArgumentException("Target host may not be null.");
		}
		if (params == null) {
			throw new IllegalArgumentException("Parameters may not be null.");
		}

		if(App.DEBUG) Log.e(TAG + "delete", "ConnectSocket with " + "\n\t host=" + host + "\n\t port=" + port + "\n\t localport=" + localPort);

		SSLSocket sslsock = (SSLSocket) ((sock != null) ? sock : createSocket());

		if ((localAddress != null) || (localPort > 0)) {
			if (localPort < 0)
				localPort = 0;

			InetSocketAddress isa = new InetSocketAddress(localAddress, localPort);
			sslsock.bind(isa);
		}

		int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
		int soTimeout = HttpConnectionParams.getSoTimeout(params);

		InetSocketAddress remoteAddress = new InetSocketAddress(host, port);

		sslsock.connect(remoteAddress, connTimeout);

		sslsock.setSoTimeout(soTimeout);

		try {
			hostnameVerifier.verify(host, sslsock);
		} catch (IOException iox) {
			try {
				sslsock.close();
			} catch (Exception x) {
			}

			throw iox;
		}

		return sslsock;
	}

	@Override
	public Socket createSocket() throws IOException {
		return socketFactory.createSocket();
	}

	@Override
	public boolean isSecure(Socket sock) throws IllegalArgumentException {
		if (sock == null) {
			throw new IllegalArgumentException("Socket may not be null.");
		}

		if (!(sock instanceof SSLSocket)) {
			throw new IllegalArgumentException("Socket not created by this factory.");
		}

		if (sock.isClosed()) {
			throw new IllegalArgumentException("Socket is closed.");
		}
		return true;
	}

	@Override
	public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
		SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket(socket, host, port, autoClose);
		hostnameVerifier.verify(host, sslSocket);
		return sslSocket;
	}

}
