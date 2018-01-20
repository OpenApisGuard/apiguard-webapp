package org.apiguard.filters;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;

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

@Component
public class RedirectFilter extends BaseFilter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		logEvent((HttpServletRequest) request);

		String queryString = ((HttpServletRequest) request).getQueryString();
		if (queryString != null) {
			RedirectAwareResponseWrapper res = new RedirectAwareResponseWrapper((HttpServletResponse) response);
			chain.doFilter(request, res);
			if (res.isRedirected()) {
				((HttpServletResponse) response).sendRedirect(res.getLocation() + "?" + queryString);
			}
		} else {
			chain.doFilter(request, response);
			logEvent((HttpServletResponse) response);
		}
	}

	@Override
	public void destroy() {
	}

	class RedirectAwareResponseWrapper extends HttpServletResponseWrapper {

		private boolean redirected = false;
		private String location;

		public RedirectAwareResponseWrapper(HttpServletResponse response) {
			super(response);
		}

		@Override
		public void sendRedirect(String location) throws IOException {
			redirected = true;
			this.location = location;
			// IMPORTANT: don't call super() here
		}

		public boolean isRedirected() {
			return redirected;
		}

		public String getLocation() {
			return location;
		}

	}
}
