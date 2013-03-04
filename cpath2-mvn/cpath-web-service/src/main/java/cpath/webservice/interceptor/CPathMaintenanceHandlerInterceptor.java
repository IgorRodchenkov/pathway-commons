package cpath.webservice.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import cpath.config.CPathSettings;

/**
 * @author rodche
 *
 */
public final class CPathMaintenanceHandlerInterceptor extends HandlerInterceptorAdapter
{

	private static final Log LOG = LogFactory.getLog(CPathMaintenanceHandlerInterceptor.class);

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
			Object handler) throws Exception {
		
		String requestUri = request.getRequestURI();
		
		if(CPathSettings.isMaintenanceEnabled() 
			&& !(requestUri.contains("/resources/") 
				|| requestUri.contains("/help")
				|| requestUri.contains("/admin/"))	
		) 
		{
			response.sendError(503, CPathSettings.property(CPathSettings.PROVIDER_NAME)
				+ " service maintenance.");
			return false;
		}
		else
			return true; 
	}
}
