// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package apps.testwsapp;
import java.io.IOException;
import java.util.Date;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.net.HttpURLConnection;

import javax.jws.WebService;
import javax.xml.ws.WebServiceRef;
import javax.xml.ws.Service;

import apps.testwsapp.client.TestWSApp;

/**
 * Simple HTTP servlet class for a ws client to
 * request load balancing info from jax-ws webservice
 */
public class TestWSAppServlet extends HttpServlet {

    @WebServiceRef(name = "ref1", wsdlLocation = "TestWSApp.wsdl")
    Service service;

    // Handles the HTTP GET request
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String result = "";
        try {
            TestWSApp wsSvc = service.getPort(TestWSApp.class);
            result = wsSvc.checkInetAddressAndHost();
        } catch (Exception e) {
            result = e.getMessage();
        }

        HttpSession session = request.getSession();
        ServletOutputStream out = response.getOutputStream();

        response.setContentType("text/xml; charset=UTF-8");
        out.println("<!DOCTYPE html>");
        out.println("<html><body>");
        out.println("\nTime stamp: " + new Date().toString());
        out.println("<br>" + result + "</br>");
        out.println("</BODY>");
        out.println("</HTML>");
    }

}
