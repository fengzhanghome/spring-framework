/*
 * Copyright 2002-2013 the original author or authors.
 *
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
 */

package org.springframework.web.socket.sockjs.transport.session;

import java.io.IOException;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.sockjs.SockJsException;
import org.springframework.web.socket.sockjs.SockJsTransportFailureException;
import org.springframework.web.socket.sockjs.support.frame.SockJsFrame;
import org.springframework.web.socket.sockjs.support.frame.SockJsFrame.FrameFormat;
import org.springframework.web.socket.sockjs.support.frame.SockJsMessageCodec;

/**
 * A SockJS session for use with streaming HTTP transports.
 *
 * @author Rossen Stoyanchev
 */
public class StreamingSockJsSession extends AbstractHttpSockJsSession {

	private int byteCount;


	public StreamingSockJsSession(String sessionId, SockJsServiceConfig config, WebSocketHandler handler) {
		super(sessionId, config, handler);
	}


	@Override
	public synchronized void setInitialRequest(ServerHttpRequest request, ServerHttpResponse response,
			FrameFormat frameFormat) throws SockJsException {

		super.setInitialRequest(request, response, frameFormat);

		// the WebSocketHandler delegate may have closed the session
		if (!isClosed()) {
			super.setLongPollingRequest(request, response, frameFormat);
		}
	}

	@Override
	protected void flushCache() throws SockJsTransportFailureException {

		cancelHeartbeat();

		do {
			String message = getMessageCache().poll();
			SockJsMessageCodec messageCodec = getSockJsServiceConfig().getMessageCodec();
			SockJsFrame frame = SockJsFrame.messageFrame(messageCodec, message);
			writeFrame(frame);

			this.byteCount += frame.getContentBytes().length + 1;
			if (logger.isTraceEnabled()) {
				logger.trace(this.byteCount + " bytes written so far, "
						+ getMessageCache().size() + " more messages not flushed");
			}
			if (this.byteCount >= getSockJsServiceConfig().getStreamBytesLimit()) {
				if (logger.isTraceEnabled()) {
					logger.trace("Streamed bytes limit reached. Recycling current request");
				}
				resetRequest();
				break;
			}
		} while (!getMessageCache().isEmpty());

		scheduleHeartbeat();
	}

	@Override
	protected synchronized void resetRequest() {
		super.resetRequest();
		this.byteCount = 0;
	}

	@Override
	protected synchronized void writeFrameInternal(SockJsFrame frame) throws IOException {
		if (isActive()) {
			super.writeFrameInternal(frame);
			getResponse().flush();
		}
	}

}

