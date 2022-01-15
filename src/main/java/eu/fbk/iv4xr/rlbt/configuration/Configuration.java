package eu.fbk.iv4xr.rlbt.configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.io.FileUtils;


/**
 * Empty configuration. Extend the class adding default elements in the parameters map.ß
 * @author prandi
 *
 */
public abstract class Configuration {
	
	protected Map<String, Object> parameters = new LinkedHashMap<String, Object>();
	
	
	public Object getParameterValue(String parameterName) {
		if (!parameters.containsKey(parameterName)) {
			throw new RuntimeException("Invalid Burlap parameter "+parameterName);
		}else {
			return parameters.get(parameterName);			
		}
	}
	
	public boolean setParameterValue(String parameterName, Object value) {
		if (!parameters.containsKey(parameterName)) {
			return false;
		}else {
			Object currentValue = parameters.get(parameterName);
			
			if (currentValue instanceof Double) {
				parameters.put(parameterName, Double.parseDouble((String) value));
				return true;
			}
			if (currentValue instanceof Integer) {
				parameters.put(parameterName, Integer.parseInt((String) value));
				return true;
			}
			if (currentValue instanceof Boolean) {
				parameters.put(parameterName, Boolean.parseBoolean((String) value));
				return true;
			}
			if (currentValue instanceof String) {
				parameters.put(parameterName, (String) value);
				return true;
			}
			
			System.err.println(currentValue.getClass().toString()+" not supported");
			return false;
		}
		
	}
	
	public boolean updateParameters(String propertyFile, List<String> parametersToIgnore) {
		// Load file as property
		Properties fileConfiguration = new Properties();
		try {
			fileConfiguration.load(new BufferedReader(new FileReader(propertyFile)));
		} catch (IOException e) {
			System.err.println("Problem in loading property file "+propertyFile);
			e.printStackTrace();
		}
		boolean setParameterValue = false;
		// iterate over properties
		for(Object par : fileConfiguration.keySet()) {
			// skip parameters to ignore
			if (parametersToIgnore != null) {
				if (parametersToIgnore.contains((String) par)) continue;
			}
			// skip comments, should already be handled by Property
//			if (((String) par).charAt(0) == '#') continue;
			Object object = fileConfiguration.get(par);
			setParameterValue = this.setParameterValue((String)par, object);
			if (!setParameterValue) {
				System.err.println("Burlap parameter "+(String)par+" not recognized");
				return false;
			}
		}
		return true;
	}
	
	public boolean updateParameters(String propertyFile) {	
		return updateParameters(propertyFile, null);
	}
	
	
	public boolean writeToFile (String outputFile) {
		boolean success = true;
		StringBuffer buffer = new StringBuffer();
		if (parameters != null) {
			for (Entry<String, Object> entry : parameters.entrySet()) {
				buffer.append(entry.getKey() + "=" + entry.getValue().toString() + "\n");
			}
		}else {
			success = false;
		}
		try {
			FileUtils.writeStringToFile(new File(outputFile), buffer.toString(), Charset.defaultCharset());
		} catch (IOException e) {
			success = false;
			e.printStackTrace();
		}
		return success;
	}

}
