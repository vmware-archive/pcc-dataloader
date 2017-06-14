package io.spring.controller;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.query.Query;
import org.apache.geode.cache.query.QueryService;
import org.apache.geode.cache.query.SelectResults;
import org.apache.geode.pdx.JSONFormatter;
import org.apache.geode.pdx.PdxInstance;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoaderController {
	
	@Autowired 
	ClientCache clientCache;
	
	@Autowired
	Region<String, PdxInstance> transactionRegion;
	
	@Autowired
    private SimpMessagingTemplate webSocket;
	
	Boolean start = null;
	

	@RequestMapping(method = RequestMethod.GET, path = "/start")
	@ResponseBody
	public String start(@RequestParam(value = "batch_size", required = true) String BATCH_SIZE) throws Exception {
		Map<String, PdxInstance> buffer = new HashMap<>();
		
		FileInputStream fis = null;
		BufferedReader reader = null;
		start = true;
		
		try {
			URL fileLocation = new URL("https://s3.amazonaws.com/phd-sample-data/trans_fact.gz");
			InputStream gzipStream = new GZIPInputStream(fileLocation.openStream());
			reader = new BufferedReader(new InputStreamReader(gzipStream));

			String attributes = "ssn|first|last|gender|street|city|state|zip|latitude|longitude|city_pop|job|dob|account_num|profile|transaction_num|transaction_date|category|amount";
			String[] keys = attributes.split("\\|");
			
			String line = reader.readLine();
			int index = count()+1;
			
            while (line != null && start) {
            	String[] values = line.split("\\|");
            	Map<String, String> customerMap = new HashMap<>();
            	
            	if (keys.length != values.length) continue;
            	
                for (int i=0; i<keys.length; i++) {
                	customerMap.put(keys[i], values[i]);
                }
                PdxInstance customer = JSONFormatter.fromJSON(new JSONObject(customerMap).toString());

                buffer.put(UUID.randomUUID().toString(), customer);   
                
                if (index % 1000 == 0) {
                	webSocket.convertAndSend("/topic/record_stats", index);
                }
                
                if (index % Integer.parseInt(BATCH_SIZE) == 0) {
                	transactionRegion.putAll(buffer);
                	webSocket.convertAndSend("/topic/record_stats", index);
                	buffer.clear();
                }
                
                line = reader.readLine();
                index++;
                Thread.sleep(50);
            }
            
            transactionRegion.putAll(buffer);
            webSocket.convertAndSend("/topic/record_stats", count());
			
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
            try {
                if (reader != null) reader.close();
                if (fis != null) fis.close();
            } catch (Exception ex) {
            	ex.printStackTrace();
            }
        }

		return "Successfully Loaded.";
	}
	
	@RequestMapping(method = RequestMethod.GET, path = "/clear")
	@ResponseBody
	public String clear() throws Exception {
		transactionRegion.removeAll(transactionRegion.keySetOnServer());
		
		return "Region cleared";
	}
	
	@RequestMapping(method = RequestMethod.GET, path = "/stop")
	@ResponseBody
	public String stop() throws Exception {
		start = false;
		
		return "Stopped.";
	}
	
	@RequestMapping(method = RequestMethod.GET, path = "/count")
	@ResponseBody
	public int count() throws Exception {
		QueryService queryService = clientCache.getQueryService();
		Query query = queryService.newQuery("SELECT count(*) FROM /" + transactionRegion.getName());
		Object result = query.execute();
		Collection<?> collection = ((SelectResults<?>)result).asList();
		
		return (Integer) collection.iterator().next();
	}
	
}
