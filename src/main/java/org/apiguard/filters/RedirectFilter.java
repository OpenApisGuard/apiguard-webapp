package org.apiguard.filters;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.springframework.stereotype.Service;

@Service
public class RedirectFilter implements Filter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		String queryString = ((HttpServletRequest) request).getQueryString();
		if (queryString != null) {
			RedirectAwareResponseWrapper res = new RedirectAwareResponseWrapper((HttpServletResponse) response);
			chain.doFilter(request, res);
			if (res.isRedirected()) {
				((HttpServletResponse) response).sendRedirect(res.getLocation() + "?" + queryString);
			}
		} else {
			chain.doFilter(request, response);
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
