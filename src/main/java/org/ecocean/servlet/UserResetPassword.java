/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.servlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.CommonConfiguration;
import org.ecocean.Shepherd;
import org.ecocean.User;


public class UserResetPassword extends HttpServlet {

  @Override
public void init(final ServletConfig config) throws ServletException {
    super.init(config);
  }


  @Override
public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  @Override
public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {

    String context="context0";
    //context=ServletUtilities.getContext(request);

    //set up the user directory
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File usersDir=new File(shepherdDataDir.getAbsolutePath()+"/users");
    if(!usersDir.exists()){usersDir.mkdirs();}

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();

    //create a new Role from an encounter

    if ((request.getParameter("username") != null) &&  (!request.getParameter("username").trim().equals("")) && (((request.getParameter("password") != null) &&  (!request.getParameter("password").trim().equals("")) && (request.getParameter("password2") != null) &&  (!request.getParameter("password2").trim().equals(""))))) {

      String username=request.getParameter("username").trim();

      String password="";
      password=request.getParameter("password").trim();
      String password2="";
      password2=request.getParameter("password2").trim();

      if((password.equals(password2))){
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        if(myShepherd.getUserOLD(username)!=null){

          User myUser=myShepherd.getUserOLD(username);

          //OK, now check OTP, time, and username hash for validity
          String OTP=request.getParameter("OTP");
          String time=request.getParameter("time");

          String matchingOtpString=myUser.getPassword()+time+myUser.getSalt();
          matchingOtpString=ServletUtilities.hashAndSaltPassword(matchingOtpString, myUser.getSalt());

          //log it
          //about to compare
          //System.out.println("OTP is: "+OTP);
          //System.out.println("matchOTP is: "+matchingOtpString);

          if(matchingOtpString.equals(OTP)){

            //set the new password
            myUser.setPassword(ServletUtilities.hashAndSaltPassword(password2, myUser.getSalt()));
            myShepherd.commitDBTransaction();

          //output success statement
            out.println(ServletUtilities.getHeader(request));

              out.println("<strong>Success:</strong> Password successfully reset.");

            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/login.jsp" + "\">Return to login page" + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
          }
          else{
            //bad params
          //output failure statement
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Failure:</strong> Password was NOT successfully reset. URL parameters could not be validated.");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "\">Return to homepage" + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
            myShepherd.rollbackDBTransaction();
          }




        }
      else{
        //no user
      //output failure statement
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> Password was NOT successfully reset. I could not find the specified user.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "\">Return to homepage" + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
        myShepherd.rollbackDBTransaction();
      }

        myShepherd.closeDBTransaction();
    }
    else{
        //output failure statement
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> Password was NOT successfully reset. Your passwords did not match.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "\">Return to homepage" + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      }


}
else{
  //output failure statement
  out.println(ServletUtilities.getHeader(request));
  out.println("<strong>Failure:</strong> User was NOT successfully created. I did not have all of the information I needed.");
  out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/users.jsp?context=context0" + "\">Return to User Administration" + "</a></p>\n");
  out.println(ServletUtilities.getFooter(context));

}






    out.close();

  }
}


