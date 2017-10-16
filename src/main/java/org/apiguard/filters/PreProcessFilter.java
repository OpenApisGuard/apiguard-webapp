package org.apiguard.filters;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apiguard.rest.controller.AdminController;
import org.apiguard.rest.controller.ClientController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

/*
 * Copyright 2017 the original author or authors.
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

@Component(value="preProcessFilter")
@PropertySource("classpath:global.properties")
public class PreProcessFilter implements Filter {

	@Value("${apiguard.system.admin.userid}")
	private String adminId;

	@Value("${apiguard.system.admin.password}")
	private String adminPwd;

	private static String secret;

	@PostConstruct
	public void setup() throws ServletException {
		// get configured system credential, secret will be adminId:adminPwd
		if (StringUtils.isEmpty(adminId) || StringUtils.isEmpty(adminPwd)) {
			throw new ServletException("System admin ID/password are not defined in properties file.");
		}

		secret = adminId + ":" + adminPwd;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		RequestWrapper apiReq = new RequestWrapper(request);
		if (request.getRequestURI().startsWith(AdminController.ADMIN_URL) || request.getRequestURI().startsWith(ClientController.ADMIN_URL)) {
			String token = ((HttpServletRequest) request).getHeader(HttpHeaders.AUTHORIZATION);;
			if (StringUtils.isEmpty(token) || ! isValid(getSignString(apiReq), token)) {
				HttpServletResponse resp = (HttpServletResponse) response;
				resp.setStatus(HttpStatus.BAD_REQUEST.value());
				resp.setContentType("application/json");
				resp.setCharacterEncoding("UTF-8");
				resp.getWriter().write("{\"message\" : \"Invalid request: " + apiReq.getRequestURI() + "\"}");
				return;
			}
		}

		chain.doFilter(apiReq, response);
	}

	@Override
	public void destroy() {
	}

	/**
	 * Signing string format:
	 * uri
	 * date
	 * base64(payload)
	 *
	 * Example signing string:
	 *
	 * /apiguard/apis
	 * Wed, 04 Oct 2017 17:35:45
	 * ew0gICJyZXF1ZXN0X3VyaSI6ICIvZ29vZ2xlMy8oLiopL1swLTldKyIsDSAgIm5hbWUiOiAiZ29vZ2xlMyIsDSAgImRvd25zdHJlYW1fdXJpIjogImh0dHBzOi8vd3d3Lmdvb2dsZS5jb20iDX0=
	 *
	 * @param request
	 * @return
	 * @throws IOException
	 */
	private String getSignString(HttpServletRequest request) throws IOException {
		String date = ((HttpServletRequest) request).getHeader(HttpHeaders.DATE);
		String body = ((RequestWrapper) request).getPayload();
		String base64Body = Base64.encodeBase64String(body.getBytes());

		StringBuilder sb = new StringBuilder(request.getScheme());
		sb.append("://");
		sb.append(request.getServerName());
		sb.append(("http".equals(request.getScheme()) && request.getServerPort() == 80) || ("https".equals(request.getScheme()) && request.getServerPort() == 443) ? "" : ":" + request.getServerPort() );
		sb.append(request.getRequestURI());
		sb.append(request.getQueryString() != null ? "?" + request.getQueryString() : "");
		sb.append(" ");
		sb.append(date);
		sb.append(" ");
		sb.append(base64Body);

		return sb.toString();
	}

	/**
	 *
	 *
	 * @param message
	 * @param token
	 * @return
	 * @throws ServletException
	 */
	private boolean isValid(String message, String token)  throws ServletException {
		try {
			if (StringUtils.isEmpty(message) || StringUtils.isEmpty(token)) {
				return false;
			}

			String hash = getHash(message, this.secret);
			String signature = Base64.encodeBase64String(hash.getBytes());
			return signature.equals(token);
		}
		catch(Exception e) {
			throw new ServletException(e);
		}
	}

	private String getHash(String message, String secret) throws Exception{
		Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
		SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256");
		sha256_HMAC.init(secret_key);

		byte[] byteData = sha256_HMAC.doFinal(message.getBytes("UTF-8"));
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < byteData.length; i++) {
			sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
		}
		return sb.toString();
	}

	class RequestWrapper extends HttpServletRequestWrapper {

		private final String payload;

		public RequestWrapper(HttpServletRequest request) throws ServletException {
			super(request);

			// read the original payload into the payload variable
			StringBuilder stringBuilder = new StringBuilder();
			BufferedReader bufferedReader = null;
			try {
				// read the payload into the StringBuilder
				InputStream inputStream = request.getInputStream();
				if (inputStream != null) {
					bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
					char[] charBuffer = new char[128];
					int bytesRead = -1;
					while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
						stringBuilder.append(charBuffer, 0, bytesRead);
					}
				} else {
					// make an empty string since there is no payload
					stringBuilder.append("");
				}
			} catch (IOException ex) {
				throw new ServletException("Error reading the request payload", ex);
			} finally {
				if (bufferedReader != null) {
					try {
						bufferedReader.close();
					} catch (IOException iox) {
						// ignore
					}
				}
			}
			payload = stringBuilder.toString();
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(payload.getBytes());
			ServletInputStream inputStream = new ServletInputStream() {
				public int read()
						throws IOException {
					return byteArrayInputStream.read();
				}
			};
			return inputStream;
		}

		public String getPayload() {
			return payload;
		}
	}
}
