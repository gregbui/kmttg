package com.tivo.kmttg.httpserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONFile;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class kmttgServer extends HTTPServer {
   private Stack<Transcode> transcodes = new Stack<Transcode>();
   public int transcode_counter = 0;
   
   public kmttgServer() {
      try {
         String baseDir = config.programDir;
         if ( ! file.isDir(baseDir)) {
            log.error("httpserver base directory not found: " + baseDir);
            return;
         }
         config.httpserver = new kmttgServer(config.httpserver_port);
         config.httpserver_home = baseDir + File.separator + "web";
         config.httpserver_cache = config.httpserver_home + File.separator + "cache";
         config.httpserver_cache_relative = "/web/cache/";
         VirtualHost host = config.httpserver.getVirtualHost(null);
         host.setAllowGeneratedIndex(true);
         host.addContext("/", new FileContextHandler(new File(baseDir), "/"));
         config.httpserver.start();
      } catch (IOException e) {
         log.error("HTTPServer Init - " + e.getMessage());
      }
   }
   
   public kmttgServer(int port) {
      super(port);
   }
   
   // Override parent method to handle special requests
   // Intercept certain special paths, pass along the rest
   protected void serve(Request req, Response resp) throws IOException {
      String path = req.getPath();
      debug.print("req path: " + path);
      
      // Clear up finished transcodes
      cleanup();
      
      // Handle rpc requests
      if (path.equals("/rpc")) {
         handleRpc(req, resp);
         return;
      }
      
      // Return list of rpc enabled TiVos known by kmttg
      if (path.equals("/getRpcTivos")) {
         handleRpcTivos(resp);
         return;
      }
      
      // Get list of video files in kmttg video places
      if (path.equals("/getVideoFiles")) {
         handleVideoFiles(resp);
         return;
      }
      
      // Get list of video files from a tivo
      if (path.equals("/getMyShows")) {
         handleMyShows(req, resp);
         return;
      }
      
      // Initiate and return transcoding video stream
      if (path.equals("/transcode")) {
         handleTranscode(req, resp);
         return;
      }
      
      // This is normal/default handling
      ContextHandler handler = req.getVirtualHost().getContext(path);
      if (handler == null) {
          resp.sendError(404);
          return;
      }
      // serve request
      int status = 404;
      // add directory index if necessary
      if (path.endsWith("/")) {
          String index = req.getVirtualHost().getDirectoryIndex();
          if (index != null) {
              req.setPath(path + index);
              status = handler.serve(req, resp);
              req.setPath(path);
          }
      }
      if (status == 404)
          status = handler.serve(req, resp);
      if (status > 0)
          resp.sendError(status);
   }
   
   // Handle rpc requests
   // Sample rpc request: /rpc?tivo=Roamio&operation=SysInfo
   public void handleRpc(Request req, Response resp) throws IOException {
      Map<String,String> params = req.getParams();
      if (params.containsKey("operation") && params.containsKey("tivo")) {
         try {
            String operation = params.get("operation");
            String tivo = string.urlDecode(params.get("tivo"));
            
            if (operation.equals("keyEventMacro")) {
               // Special case
               String sequence = string.urlDecode(params.get("sequence"));
               String[] s = sequence.split(" ");
               Remote r = new Remote(tivo);
               if (r.success) {
                  r.keyEventMacro(s);
                  resp.send(200, "");
               } else {
                  resp.sendError(500, "RPC call failed to TiVo: " + tivo);
               }
               return;
            }
            
            if (operation.equals("SPSave")) {
               // Special case
               String fileName = config.programDir + File.separator + tivo + ".sp";
               Remote r = new Remote(tivo);
               if (r.success) {
                  JSONArray a = r.SeasonPasses(null);
                  if ( a != null ) {
                     if ( ! JSONFile.write(a, fileName) ) {
                        resp.sendError(500, "Failed to write to file: " + fileName);
                     }
                  } else {
                     resp.sendError(500, "Failed to retriev SP list for tivo: " + tivo);
                     r.disconnect();
                     return;
                  }
                  r.disconnect();
                  resp.send(200, "Saved SP to file: " + fileName);
               } else {
                  resp.sendError(500, "RPC call failed to TiVo: " + tivo);
               }
               return;
            }
            
            if (operation.equals("SPFiles")) {
               // Special case - return all .sp files available for loading
               File dir = new File(config.programDir);
               File [] files = dir.listFiles(new FilenameFilter() {
                   public boolean accept(File dir, String name) {
                       return name.endsWith(".sp");
                   }
               });
               JSONArray a = new JSONArray();
               for (File f : files) {
                  a.put(f.getAbsolutePath());
               }
               resp.send(200, a.toString());
               return;
            }
            
            if (operation.equals("SPLoad")) {
               // Special case
               String fileName = string.urlDecode(params.get("file"));
               JSONArray a = JSONFile.readJSONArray(fileName);
               if ( a != null ) {
                  resp.send(200, a.toString());
               } else {
                  resp.sendError(500, "Failed to load SP file: " + fileName);
               }
               return;
            }
            
            // General purpose remote operation
            JSONObject json;
            if (params.containsKey("json"))
               json = new JSONObject(string.urlDecode(params.get("json")));
            else
               json = new JSONObject();
            Remote r = new Remote(tivo);
            if (r.success) {
               JSONObject result = r.Command(operation, json);
               if (result != null) {
                  resp.send(200, result.toString());
               }
               r.disconnect();
            }
         } catch (Exception e) {
            resp.sendError(500, e.getMessage());
         }
      } else {
         resp.sendError(400, "RPC request missing 'operation' and/or 'tivo'");
      }
   }
   
   // Return list of rpc enabled TiVos known by kmttg
   public void handleRpcTivos(Response resp) throws IOException {
      Stack<String> tivos = config.getTivoNames();
      JSONArray a = new JSONArray();
      for (String tivoName : tivos) {
         if (config.rpcEnabled(tivoName))
            a.put(tivoName);
      }
      resp.send(200, a.toString());
   }
   
   // Return list of rpc enabled TiVos known by kmttg
   public void handleVideoFiles(Response resp) throws IOException {
      // LinkedHashMap is used to get unique list of files
      LinkedHashMap<String,Integer> h = new LinkedHashMap<String,Integer>();
      getVideoFiles(config.mpegDir, h);
      getVideoFiles(config.outputDir, h);
      getVideoFiles(config.encodeDir, h);
      JSONArray a = new JSONArray();
      for (String key : h.keySet()) {
         a.put(key);
      }
      resp.send(200, a.toString());
   }
   
   public void handleMyShows(Request req, Response resp) throws IOException {
      Map<String,String> params = req.getParams();
      if (params.containsKey("tivo")) {
         String tivo = string.urlDecode(params.get("tivo"));
         Remote r = new Remote(tivo);
         if (r.success) {
            jobData job = new jobData();
            job.tivoName = tivo;
            job.getURLs = true; // This needed to get __url__ property
            JSONArray a = r.MyShows(job);
            r.disconnect();
            resp.send(200, a.toString());         
         } else {
            resp.sendError(500, "Failed to get shows from tivo: " + tivo);
            return;
         }
      } else {
         resp.sendError(400, "Request missing tivo parameter");
      }
   }
   
   private void getVideoFiles(String pathname, LinkedHashMap<String,Integer> h) {
      File f = new File(pathname);
      File[] listfiles = f.listFiles();
      for (int i = 0; i < listfiles.length; i++) {
         if (listfiles[i].isDirectory()) {
            File[] internalFile = listfiles[i].listFiles();
            for (int j = 0; j < internalFile.length; j++) {
               if (isVideoFile(internalFile[j].getAbsolutePath()))
                  h.put(internalFile[j].getAbsolutePath(), 1);
               if (internalFile[j].isDirectory()) {
                  String name = internalFile[j].getAbsolutePath();
                  getVideoFiles(name, h);
               }
            }
         } else {
            if (isVideoFile(listfiles[i].getAbsolutePath()))
               h.put(listfiles[i].getAbsolutePath(), 1);
         }
      }
   }
   
   private boolean isVideoFile(String fileName) {
      boolean videoFile = false;
      String[] extensions = {
         "mp4","mpeg","vob","mpg","mpeg2","mp2","avi","wmv",
         "asf","mkv","tivo","m4v","3gp","mov","flv","ts"
      };
      for (String extension : extensions) {
         if (fileName.toLowerCase().endsWith("." + extension))
            videoFile = true;
      }
      return videoFile;
   }

   // Transcoding video handler
   public void handleTranscode(Request req, Response resp) throws IOException {
      Map<String,String> params = req.getParams();
      
      if (params.containsKey("killall")) {
         int num = killTranscodes();
         resp.send(200, "Killed " + num + " jobs");
         return;
      }
      
      if (params.containsKey("kill")) {
         String fileName = string.urlDecode(params.get("kill"));
         killTranscode(fileName);
         resp.send(200, "Killed job: " + fileName);
         return;
      }
      
      if (params.containsKey("running")) {
         JSONArray a = getRunning();
         if (a.length() == 0)
            a.put("NONE");
         resp.send(200, a.toString());
         return;
      }
      
      if (params.containsKey("getCached")) {
         JSONArray a = getCached();
         if (a.length() == 0)
            a.put("NONE");
         resp.send(200, a.toString());
         return;
      }
      
      // File transcode
      Transcode tc = null;
      String returnFile = null;
      SocketProcessInputStream ss = null;
      long length = 0;
      if (params.containsKey("file") && params.containsKey("format")) {
         String fileName = string.urlDecode(params.get("file"));
         if ( ! file.isFile(fileName) ) {
            resp.sendError(404, "Cannot find video file: '" + fileName + "'");
            return;
         }
         tc = alreadyRunning(fileName);
         if (tc != null) {
            if (tc.ss != null)
               ss = tc.ss;
            if (tc.returnFile != null)
               returnFile = tc.returnFile;
         } else {
            String format = string.urlDecode(params.get("format"));
            length = new File(fileName).length();
            tc = new Transcode(fileName);
            addTranscode(tc);
            if (format.equals("webm"))
               ss = tc.webm();
            else if (format.equals("hls"))
               returnFile = tc.hls();
            else {
               resp.sendError(500, "Unsupported transcode format: " + format);
               return;
            }
         }
      }
      
      // TiVo download + transcode
      if (params.containsKey("url") && params.containsKey("format") && params.containsKey("name")) {
         String url = params.get("url");
         tc = alreadyRunning(url);
         if (tc != null) {
            if (tc.returnFile != null)
               returnFile = tc.returnFile;
         } else {
            String format = string.urlDecode(params.get("format"));
            String name = string.urlDecode(params.get("name"));
            tc = new TiVoTranscode(url, name);
            addTranscode(tc);
            if (format.equals("hls"))
               returnFile = tc.hls();
            else {
               resp.sendError(500, "Unsupported transcode format: " + format);
               return;
            }
         }
      }
      
      if (ss != null || returnFile != null) {
         // Transcode stream has been started, so send it out
         try {
            if (ss != null) {
               resp.sendBody(ss, length, null);
            }
            if (returnFile != null) {
               req.setPath(returnFile);
               //resp.sendHeaders(200, -1, -1, null, "application/x-mpegurl", null);
               serve(req, resp);
            }
         } catch (Exception e) {
            // This catches interruptions from client side so we can kill the transcode
            log.error("transcode - " + e.getMessage());
            if (ss != null)
               ss.close();
            tc.kill();
         }
      } else {
         resp.sendError(500, "Error starting transcode");
      }      
   }
   
   private void addTranscode(Transcode tc) {
      transcodes.add(tc);
      transcode_counter++;
   }
   
   // Restrict to only 1 transcode at a time per input file
   Transcode alreadyRunning(String fileName) {
      for (Transcode tc : transcodes) {
         if (tc.inputFile.equals(fileName)) {
            if (tc.isRunning())
               return tc;
         }
      }
      return null;
   }
   
   JSONArray getRunning() {
      JSONArray a = new JSONArray();
      for (Transcode tc : transcodes) {
         if (tc.isRunning())
            a.put(tc.inputFile);
      }
      return a;
   }
   
   JSONArray getCached() {
      JSONArray a = new JSONArray();
      String base = config.httpserver_cache;
      if (! file.isDir(base))
         return a;
      File[] files = new File(base).listFiles();
      for (File f : files) {
         if (f.getAbsolutePath().endsWith(".m3u8")) {
            try {
            JSONObject json = new JSONObject();
            json.put("url", config.httpserver_cache_relative + string.basename(f.getAbsolutePath()));
            String textFile = f.getAbsolutePath() + ".txt";
            if (file.isFile(textFile))
               json.put("name", getTextFileContents(textFile));
            a.put(json);
            } catch (JSONException e) {
               log.error("getCached - " + e.getMessage());
            }
         }
      }
      return a;
   }
   
   String getTextFileContents(String textFile) {
      String text = "";
      try {
         text = new Scanner(new File(textFile)).useDelimiter("\\A").next();
      } catch (FileNotFoundException e) {
         log.error("getTextFileContents - " + e.getMessage());
      }
      return text;
   }
   
   // Remove finished processes from transcodes stack
   void cleanup() {
      for (int i=0; i<transcodes.size(); ++i) {
         Transcode tc = transcodes.get(i);
         if (! tc.isRunning()) {
            //tc.cleanup();
            transcodes.remove(i);
         }
      }
   }
  
   void killTranscode(String name) {
      boolean removed = false;
      log.warn("killTranscode - " + name);
      name = name.replaceFirst(config.httpserver_cache_relative, "");
      for (int i=0; i<transcodes.size(); ++i) {
         Transcode tc = transcodes.get(i);
         String prefix = tc.prefix;
         if (name.startsWith(prefix)) {
            tc.kill();
            //tc.cleanup();
            transcodes.remove(i);
            removed = true;
         }
      }
      if (! removed) {
         // name might be the original full path inputFile
         for (int i=0; i<transcodes.size(); ++i) {
            Transcode tc = transcodes.get(i);
            if (name.equals(tc.inputFile)) {
               tc.kill();
               //tc.cleanup();
               transcodes.remove(i);
            }
         }
     }
   }
   
   public int killTranscodes() {
      int killed = 0;
      for(Transcode tc : transcodes) {
         tc.kill();
         //tc.cleanup();
         killed++;
      }
      cleanup();
      transcodes.clear();
      return killed;
   }
}