package com.library.cli;
import java.util.*;
public class CommandParser {
 public List<String> parse(String line){
  List<String> result=new ArrayList<>();StringBuilder token=new StringBuilder();boolean quoted=false;
  for(char c:line.toCharArray()){
   if(c=='"')quoted=!quoted;
   else if(Character.isWhitespace(c)&&!quoted){if(!token.isEmpty()){result.add(token.toString());token.setLength(0);}}
   else token.append(c);
  }
  if(quoted)throw new IllegalArgumentException("A quoted argument was not closed.");
  if(!token.isEmpty())result.add(token.toString());return result;
 }
}
