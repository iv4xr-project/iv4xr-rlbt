/**
 * 
 */
package eu.fbk.iv4xr.rlbt.utils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.opencsv.CSVReader;
/**
 * @author kifetew
 *
 */
public class SpatialCoverageGenerator {


	/**
	 * 
	 */
	private SpatialCoverageGenerator() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
//		String levelFilePath = "/Users/kifetew/workspace/projects/iv4xr/RL/WP3/iv4xr-rlbt/src/test/resources/levels/LabRecruits_random_level2-1_fixed2Agents.csv";
//		String agentLocationTracePath = "/Users/kifetew/workspace/projects/iv4xr/RL/WP3/iv4xr-rlbt/rlbt-files/results/LabRecruits_random_level2-1_fixed2Agents/multi/23159246562709/";
		
		if ((args.length != 2)
				|| !(new File(args[0]).isFile()) 
				|| !(new File(args[1]).isDirectory())) {
			System.err.println("Two inputs required: absolute_path_to_level_csv and location_traces_dir");
			System.exit(1);
		}
		
		String levelFilePath = args[0];
		String agentLocationTracePath = args[1];
		
		generateSpatialCoveragePlot(levelFilePath, agentLocationTracePath);
		System.exit(0);
	}
	
	/**
	 * produce the image showing the agents' positions
	 * @param levelFilePath: absolute path on disk of the csv file of the LabRecruits level
	 * @param agentLocationTracePath: absolute path on disk of the directory containing the agent location trace file(s)
	 * 
	 * The method produces an image showing the agent positions overlayed on the level map.
	 * The image will be saved in the same location as the trace files, with the name of the levelFilePath.png
	 * @throws IOException 
	 */
	public static void generateSpatialCoveragePlot(String levelFilePath, String agentLocationTracePath) throws IOException {
		
//		String baseLevelsDir = FilenameUtils.getFullPathNoEndSeparator(levelFilePath);
		String baseFileName = FilenameUtils.getBaseName(levelFilePath);
		
		// first calculate the map size and generate the map coordinates
		Pair<int[], String> levelMap = calculateMapSize(levelFilePath);
		int[] size = levelMap.getLeft();
		String map = getFloorMap(levelMap);
		
		// save the coordinates of the base level map
		String baseMapPath = agentLocationTracePath + File.separator + "level_floor_map.csv";
		FileUtils.writeStringToFile(new File(baseMapPath), map, Charset.defaultCharset());
		
		// invoke the python script to do the ploting
		String outputFileName = agentLocationTracePath + File.separator + baseFileName + ".png";
		callPythonScript(agentLocationTracePath, baseMapPath, size, outputFileName);
	}

	
	private static boolean callPythonScript (String agentLocationTracePath, String baseMapPath, int[] mapSize, String outputFileName) throws IOException {
		String[] params = {"/Users/kifetew/workspace/projects/iv4xr/RL/WP3/iv4xr-rlbt/generate_single_heatmap.sh", 
				agentLocationTracePath, 
				baseMapPath, 
				""+(mapSize[1]+1),
				""+(mapSize[0]+1),
				outputFileName};

		ProcessBuilder processBuilder = new ProcessBuilder(params);
	    processBuilder.redirectErrorStream(true);

		try {
			Process process = processBuilder.start();
			InputStream inputStream = process.getInputStream();
			int bytesRead = 0;
			while ((bytesRead = inputStream.read()) >= 0) {
				System.out.print((char)bytesRead);
			}
			int exitCode = process.waitFor();
			return exitCode == 0;
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	
	
	public static String getFloorMap(Pair<int[], String> mapSize) throws IOException {
		
	    CSVReader reader = new CSVReader(new StringReader(mapSize.getRight()));
	      String [] nextLine;
	      int[] size = mapSize.getLeft();
	      int x = 1;
	      int y = 1;
	      StringBuffer floorMapCsv  = new StringBuffer();
	      floorMapCsv.append("posx,posz,time" + System.lineSeparator());
	      while ((nextLine = reader.readNext()) != null) {
	    	  
	    	  y = 1;
    		  for (String cell : nextLine) {
    			  cell = cell.trim();
    			  if (cell.isEmpty()) {
    				  continue;
    			  }
    			  double data = 0;
    			  if (cell.trim().startsWith("w")) {
    				  data = 2; // wall value
    				  floorMapCsv.append(String.format("%d,%d,%f" + System.lineSeparator(), y, x, data));
    			  }
    			  else if(cell.trim().startsWith("f")) {
//    				  data = 1; // floor value
//    				  floorMapCsv.append(String.format("%d,%d,%f" + System.lineSeparator(), x, y, data));
    				  
    			  }
    			  else {
    				  // must not reach here!
    				  System.out.println(cell);
    				  continue;
    			  }
    			  y++;
    		  }
    		  x++;
	      }
	      assert(--x == size[0]);
	      assert(--y == size[1]);
	      reader.close();
	      return floorMapCsv.toString();
	}
	

		
	public static Pair<int[], String> calculateMapSize(String filepath) throws IOException {
		
	    CSVReader reader = new CSVReader(new FileReader(filepath));
	      String [] nextLine;
	      int height = 0;
	      int width = 0;
	      int floornum=0;
	      boolean floorMapStarted = false;
	      StringBuffer floorMapCsv  = new StringBuffer();
	      while ((nextLine = reader.readNext()) != null && floornum!=2) {
	    	  
	    	  if(nextLine[0].startsWith("|")) {
	    		  floornum++;
	    		  if (floornum == 1) {
	    			  floorMapStarted = true;
	    		  }else {
	    			  floorMapStarted = false;
	    		  }
	    	  }
	    	  
	    	  // process the line as floor plan if we're on the first level
	    	  if (floorMapStarted) {
	    		  for (String cell : nextLine) {
	    			  if (cell.trim().startsWith("w") || cell.trim().startsWith("|w")) {
	    				  floorMapCsv.append("w,");
	    			  }else if(cell.trim().startsWith("f")) {
	    				  floorMapCsv.append("f,");
	    			  }
	    		  }
	    		  floorMapCsv.append(System.lineSeparator());
	    	  }
	    	  
	    	  if(nextLine[0].startsWith("b"))	continue;
	    	  else height++;
	    	  
	    	  if(width<nextLine.length) width=nextLine.length;
	      }
//	      System.out.println(floorMapCsv.toString());
	      return Pair.of(new int[] {height-1,width}, floorMapCsv.toString()); 
	}
	    
	
}
