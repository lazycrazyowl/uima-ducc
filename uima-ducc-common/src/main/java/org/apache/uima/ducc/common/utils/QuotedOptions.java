/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package org.apache.uima.ducc.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuotedOptions {
    /**
     * Create an array of parameters from a whitespace-delimited list (e.g. JVM args or environment assignments.) 
     * Values containing whitespace must be single- or double-quoted:
     *  TERM=xterm DISPLAY=:1.0 LD_LIBRARY_PATH="/my/path/with blanks/" EMPTY= -Dxyz="a b c" -Dabc='x y z' 
     * Quotes may be stripped or preserved.
     * Values containing both types of quotes are NOT supported.
     * 
     * @param options
     *          - string of blank-delimited options
     * @param stripQuotes
     *          - true if balanced quotes are to be removed
     * @return - array of options
     */
    public static ArrayList<String> tokenizeList(String options, boolean stripQuotes) {
        
      ArrayList<String> tokens = new ArrayList<String>();
      if (options == null) {
        return tokens;
      }
      
      // Pattern matches a non-quoted region or a double-quoted region or a single-quoted region
      // 1st part matches one or more non-whitespace characters but not " or '
      // 2nd part matches a "quoted" region containing any character except "
      // 3rd part matches a 'quoted' region containing any character except '
      // See: http://stackoverflow.com/questions/3366281/tokenizing-a-string-but-ignoring-delimiters-within-quotes
        
      String noSpaceRegex = "[^\\s\"']+";
      String doubleQuoteRegex = "\"([^\"]*)\"";
      String singleQuoteRegex = "'([^']*)'";
      final String regex = noSpaceRegex + "|" + doubleQuoteRegex + "|" + singleQuoteRegex;     
      Pattern patn = Pattern.compile(regex);
      Matcher matcher = patn.matcher(options);
      StringBuilder sb = new StringBuilder();
      
      // If stripping quotes extract the capturing group (without the quotes)
      // When preserving quotes extract the full region
      // Combine the pieces of a token until the match ends with whitespace
      if (stripQuotes) {
        while (matcher.find()) {
          if (matcher.group(1) != null) {
            sb.append(matcher.group(1));
          } else if (matcher.group(2) != null) {
            sb.append(matcher.group(2));
          } else {
            sb.append(matcher.group());
          }
          if (matcher.end() >= options.length() || Character.isWhitespace(options.charAt(matcher.end()))) {
            tokens.add(sb.toString());
            sb.setLength(0);
          }
        }
      } else {
        while (matcher.find()) {
          sb.append(matcher.group());
          if (matcher.end() >= options.length() || Character.isWhitespace(options.charAt(matcher.end()))) {
            tokens.add(sb.toString());
            sb.setLength(0);
          }
        }
      }
      return tokens;
    }

    /*
     * Create a map from an array of environment variable assignments produced by tokenizeList Quotes
     * may have been stripped by tokenizeList The value is optional but the key is NOT, e.g. accept
     * assignments of the form foo=abc & foo= & foo reject =foo & =
     * 
     * @param assignments - list of environment or JVM arg assignments
     * @param jvmArgs - true if tokens are JVM args -- process only the -Dprop=value entries
     *  
     * @return - map of key/value pairs null if syntax is illegal
     */
    public static Map<String, String> parseAssignments(List<String> assignments, boolean jvmArgs) 
        throws IllegalArgumentException {

      HashMap<String, String> map = new HashMap<String, String>();
      if (assignments == null || assignments.size() == 0) {
        return map;
      }
      for (String assignment : assignments) {
        String[] parts = assignment.split("=", 2); // Split on first '='
        String key = parts[0];
        if (key.length() == 0) {
          throw new IllegalArgumentException("Missing key in assignment: " + assignment);
        }
        if (jvmArgs) {
          if (!key.startsWith("-D")) {
            continue;
          }
          key = key.substring(2);
        }
        map.put(key, parts.length > 1 ? parts[1] : "");
      }
      return map;
    }

    // ====================================================================================================
    
    /*
     * Test the quote handling and optional stripping 
     */
    public static void main(String[] args) {
      String[] lists = { "SINGLE_QUOTED='single quoted'\tDOUBLE_QUOTED=\"double quoted\"     SINGLE_QUOTE=\"'\" \r DOUBLE_QUOTE='\"'",
                         "",
                         "            ",
                         null };
      
      for (String list : lists) { 
        System.out.println("List: " + list);
        ArrayList<String> tokens = tokenizeList(list, false);
        System.out.println("\n  quotes preserved on " + tokens.size());
        for (String token : tokens) {
          System.out.println("~" + token + "~");
        }
        tokens = tokenizeList(list, true);
        System.out.println("\n  quotes stripped from " + tokens.size());
        for (String token : tokens) {
          System.out.println("~" + token + "~");
        }
      }
    }
}
