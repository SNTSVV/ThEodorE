/**
 * Copyright by University of Luxembourg 2019-2020.
 * Developed by Claudio Menghi, claudio.menghi@uni.lu, University of Luxembourg.
 * Developed by Enrico Vigano, enrico.vigano@ext.uni.lu, University of Luxembourg
 * Developed by Domenico Bianculli, domenico.bianculli@uni.lu, University of Luxembourg.
 * Developed by Lionel Briand,lionel.briand@uni.lu, University of Luxembourg.
 */

package lu.svv.theodore.preprocessing;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.eclipse.xtext.generator.IFileSystemAccess2;

public class Preprocessing {

	private List<Long> dates;

	private Long lastAssignment = 0L;

	private Map<String, Map<String, Set<String>>> notPresentSignals;

	private ArrayList<Integer> logsNumberOfEntries;

	private Long minSimulationTime;
	private Long maxSimulationTime;

	public Preprocessing() {

		dates = new ArrayList<>();
		notPresentSignals = new HashMap<>();
		minSimulationTime=Long.MAX_VALUE;
	 	maxSimulationTime=Long.MIN_VALUE;
	}

	public  Long getMinSimulationTime() {
		return this.minSimulationTime;
	}
	public  Long getMaxSimulationTime() {
		return this.maxSimulationTime;
	}

	/**
	 * returns the number of records added to the file
	 * @param fsa
	 * @param inputFile
	 * @param outputFile
	 * @param signals
	 * @return
	 */
	public int filter(IFileSystemAccess2 fsa, String inputFile, String outputFile, List<String> signals) {
		Scanner myReader = new Scanner(fsa.readTextFile(inputFile).toString());

		StringBuilder b = new StringBuilder();

		int num=0;
		String dateLine="";
		boolean datewritten=false;


		while (myReader.hasNextLine()) {
			String originalLine = myReader.nextLine();
			String line=originalLine;
			line = line.replaceAll("\\s+", ",");
			if (line.startsWith(",")) {
				line = line.substring(1);
			}
			String[] splitted = line.split(",");


			// if the signal match and was not already in the initialization values
			if (signals.contains(splitted[0])) {

				if(!datewritten) {
					b.append(dateLine+"\n");
					datewritten=true;
					num=num+1;

				}
				b.append(originalLine+"\n");


			} else {
				if (line != "" && line.length()>20) {
					try {
						SimpleDateFormat formatter = new SimpleDateFormat("yyyy.DDD.HH.mm.ss.SSS");

						Date d=formatter.parse(line.substring(0,line.length()-3));
						dateLine=line;
						datewritten=false;

						Long res=d.getTime() * 1000;
						if(res<minSimulationTime) {
							minSimulationTime=res;
						}
						if(res>maxSimulationTime) {
							maxSimulationTime=res;
						}



					} catch (ParseException e) {

					}
				}
			}
		}
		myReader.close();

		fsa.generateFile(outputFile, b);
		return num;
	}

	public double getSampleStep(IFileSystemAccess2 fsa, String inputFile, List<String> signals) {
		Scanner myReader = new Scanner(fsa.readTextFile(inputFile).toString());

		Date lastdate = null;

		Long minDiff=Long.MAX_VALUE;
		Set<Long> getSampledates = new HashSet<Long>();


		while (myReader.hasNextLine()) {
			String line = myReader.nextLine();
			line = line.replaceAll("\\s+", ",");
			if (line.startsWith(",")) {
				line = line.substring(1);
			}
			String[] splitted = line.split(",");


			// if the signal match and was not already in the initialization values
			if (signals.contains(splitted[0])) {

				// to put into microseconds
				if(lastdate==null) {
					myReader.close();
					throw new Error("Initial date null");
				}
				getSampledates.add(lastdate.getTime()* 1000);


			} else {
				if (line != "" && line.length()>20) {
					try {
						SimpleDateFormat formatter = new SimpleDateFormat("yyyy.DDD.HH.mm.ss.SSS");

						lastdate = formatter.parse(line.substring(0,line.length()-3));

					} catch (ParseException e) {

					}
				}
			}

		}

		List<Long> dateList=new ArrayList<>(getSampledates);
		Collections.sort(dateList);

		for (int i = 0; i < getSampledates.size() - 1; i++) {
			minDiff = Math.min(minDiff, dateList.get(i + 1) - dateList.get(i));
		}
		myReader.close();

		return minDiff;
	}
	public void checkCorrectness() {

		StringBuilder b = new StringBuilder();


		if (!notPresentSignals.keySet().isEmpty()) {
			b.append("Errors: some signals are not correctly defined:\n");
			for (String trace : notPresentSignals.keySet()) {
				for (String property : notPresentSignals.get(trace).keySet()) {
					b.append("Trace: " + trace + "\t"+"\t Property: " + property + "\n");
					//for (String signal : notPresentSignals.get(trace).get(property)) {
					//	b.append("\t\t Signal: " + signal + " is not present in the trace\n");
					//}
				}

			}
			System.out.println(b.toString());
			//throw new IllegalArgumentException(b.toString());
		}

	}

	/**
	 * given an input file it generates the output file by interpolating the signals
	 * contained in the input file
	 *
	 * @param inputFile                  contains the raw traces provided by our
	 *                                   industrial partner
	 * @param outputFile                 the file where the interpolated traces are
	 *                                   written
	 * @param signals                    the set containing the traces of interest
	 * @param sampleStep                 the desired sample step
	 * @param mapSignalInterpolationType a map that contains for each signal the
	 *                                   interpolation type considered for that
	 *                                   signal at the moment the types "Linear" and
	 *                                   "Constant" are considered
	 * @throws ParseException
	 * @throws IOException
	 */
	public boolean executeFixedSampleStep(IFileSystemAccess2 fsa, String inputFile, String outputFile, List<String> signals,
			Double sampleStep, Map<String, String> mapSignalInterpolationType, String originalTraceFilepath,
			String property, String traceName, ArrayList<Integer> logsNumberOfEntries,
			Map<String, Integer> suggestedSampleSteps) throws ParseException, IOException {

		this.logsNumberOfEntries = logsNumberOfEntries;
		dates = new ArrayList<>();
		lastAssignment = 0L;

		System.out.println("*******************************************************");
		System.out.println("Pre-processing...");
		System.out.println("Trace: " + traceName);
		System.out.println("Requirement: " + property);
		System.out.println("*******************************************************");
		Map<String, Map<Long, Double>> assignments = this.getAssignments(fsa, inputFile, signals);

		Set<String> signalsNotPresent = new HashSet();
		for (String s : signals) {
			if (!assignments.keySet().contains(s) || assignments.get(s).isEmpty()) {
				signalsNotPresent.add(s);
			}
		}
		if (!signalsNotPresent.isEmpty()) {
			if (!notPresentSignals.keySet().contains(traceName)) {
				notPresentSignals.put(traceName, new HashMap<>());
			}
			notPresentSignals.get(traceName).put(property, signalsNotPresent);
		}

		if (!assignments.keySet().isEmpty()) {
			suggestInterpolationTime(assignments, sampleStep, suggestedSampleSteps, traceName);
			generateInterpolatedFileFixedSampleStep(fsa, assignments, sampleStep, outputFile, mapSignalInterpolationType);
			System.out.println("Interpolated file generated");
			System.out.println("*******************************************************");

			return true;
		}
		System.out.println("Problems in the pre-processing");
		System.out.println("*******************************************************");

		dates = new ArrayList<>();
		return false;
	}


	public boolean executeVariableSampleStep(IFileSystemAccess2 fsa, String inputFile, String outputFile, List<String> signals,
			Double sampleStep, Map<String, String> mapSignalInterpolationType, String originalTraceFilepath,
			String property, String traceName, ArrayList<Integer> logsNumberOfEntries,
			Map<String, Integer> suggestedSampleSteps) throws ParseException, IOException {

		this.logsNumberOfEntries = logsNumberOfEntries;
		dates = new ArrayList<>();
		lastAssignment = 0L;

		//System.out.println("*******************************************************");
		//System.out.println("Pre-processing...");
		System.out.println("Trace: " + traceName+"\t Requirement:"+property);
		//System.out.println("*******************************************************");
		Map<String, Map<Long, Double>> assignments = this.getAssignments(fsa, inputFile, signals);

		Set<String> signalsNotPresent = new HashSet();
		for (String s : signals) {
			if (!assignments.keySet().contains(s) || assignments.get(s).isEmpty()) {
				signalsNotPresent.add(s);
			}
		}
		if (!signalsNotPresent.isEmpty()) {
			if (!notPresentSignals.keySet().contains(traceName)) {
				notPresentSignals.put(traceName, new HashMap<>());
			}
			notPresentSignals.get(traceName).put(property, signalsNotPresent);
		}

		if (!assignments.keySet().isEmpty()) {
			suggestInterpolationTime(assignments, sampleStep, suggestedSampleSteps, traceName);
			generateInterpolatedFileVariableSampleStep(fsa, assignments, sampleStep, outputFile, mapSignalInterpolationType);
			//System.out.println("Interpolated file generated");
			//System.out.println("*******************************************************");

			return true;
		}
		System.out.println("Problems in the pre-processing");
		System.out.println("*******************************************************");

		dates = new ArrayList<>();
		return false;
	}

	private void suggestInterpolationTime(Map<String, Map<Long, Double>> assignments, Double sampleStep,
			Map<String, Integer> suggestedSampleSteps, String tracename) {

		Long min = Long.MAX_VALUE;

		List<Long> difftimestamps = new ArrayList<>();
		for (String s : assignments.keySet()) {

			List<Long> timestamps = new ArrayList<>(assignments.get(s).keySet());

			Collections.sort(timestamps);

			for (int i = 0; i < timestamps.size() - 1; i++) {
				min = Math.min(min, timestamps.get(i + 1) - timestamps.get(i));
				difftimestamps.add(timestamps.get(i + 1) - timestamps.get(i));
			}
		}

		//System.out.println("Minimum distance between records is: " + min + "[micros]");

		if(!difftimestamps.isEmpty()) {
			Long avg = (new Double(difftimestamps.stream().mapToLong(s -> s).average().getAsDouble())).longValue();
			//System.out.println("Maximumum distance between records is: : "+ (difftimestamps.stream().mapToLong(s -> s).max()).getAsLong() + "[micros]");
			//System.out.println("Average distance between records is: : " + avg + "[micros]");

			//System.out.println("Sample step is: " + new Double(sampleStep).longValue()+ "[micros]");
			if (!suggestedSampleSteps.containsKey(tracename)) {
				suggestedSampleSteps.put(tracename, Integer.MAX_VALUE);
			} else {
				if (avg < suggestedSampleSteps.get(tracename)) {
					suggestedSampleSteps.put(tracename, min.intValue());
				}
			}

			if (min < sampleStep.longValue()) {
				//System.out.println("******************************************************************");
				//System.out.println("SAMPLE	 WARNING :::: Sample step greater than minimum sample step");
				//System.out.println("******************************************************************");

			}
		}




	}

	private void generateInterpolatedFileFixedSampleStep(IFileSystemAccess2 fsa, Map<String, Map<Long, Double>> assignments,
			Double sampleStep, String outputFile, Map<String, String> mapSignalInterpolationType) throws IOException {

		Long mintime = Long.MAX_VALUE;
		for (String s : assignments.keySet()) {

			Set<Long> timestamps = assignments.get(s).keySet();
			Long mint = timestamps.stream().min(Long::compare).orElse(Long.MAX_VALUE);
			mintime = Math.min(mintime, mint);
		}

		StringBuilder b = new StringBuilder();
		Long currentSample = 0L;

		// contains the initial timestamp of each signal
		Map<String, Long> mapSignalInitTimestamp = new HashMap<String, Long>();
		// contains the final timestamp of each signal
		Map<String, Long> mapSignalEndTimestamp = new HashMap<String, Long>();

		for (String signal : assignments.keySet()) {
			Long inittimestamp = assignments.get(signal).keySet().stream().min(Long::compare).orElse(0L);
			mapSignalInitTimestamp.put(signal, inittimestamp);
			Long endtimestamp = assignments.get(signal).keySet().stream().max(Long::compare).orElse(0L);
			mapSignalEndTimestamp.put(signal, endtimestamp);
		}

		// a map contining for each signal the ordered set of timestamps
		Map<String, List<Long>> mapSignalTimestamps = new HashMap<String, List<Long>>();
		// contains the last position analyzed for each signal
		Map<String, Integer> signalLastPosition = new HashMap<String, Integer>();
		for (String signal : assignments.keySet()) {
			List<Long> tmp = new ArrayList<Long>(assignments.get(signal).keySet());
			Collections.sort(tmp);
			mapSignalTimestamps.put(signal, tmp);
			signalLastPosition.put(signal, 0);
		}

		int num = 0;
		while (currentSample < lastAssignment - mintime +sampleStep.longValue()) {

			String line = (currentSample.toString());

			for (String signal : assignments.keySet()) {
				Double value = getInterpolatedValue(signal, assignments.get(signal), currentSample,
						mapSignalInterpolationType.get(signal), mintime, mapSignalInitTimestamp.get(signal),
						mapSignalEndTimestamp.get(signal), signalLastPosition, mapSignalTimestamps.get(signal));
				line += "," + signal + "," + value;
			}
			b.append(line + "\n");

			currentSample = currentSample + sampleStep.longValue();
			num = num + 1;
		}
		logsNumberOfEntries.add(num);
		System.out.println("Number of entries: " + num);

		fsa.generateFile(outputFile, b.toString());
	}


	private void generateInterpolatedFileVariableSampleStep(IFileSystemAccess2 fsa, Map<String, Map<Long, Double>> assignments,
			Double sampleStep, String outputFile, Map<String, String> mapSignalInterpolationType) throws IOException {

		Long mintime = Long.MAX_VALUE;
		for (String s : assignments.keySet()) {

			Set<Long> timestamps = assignments.get(s).keySet();
			Long mint = timestamps.stream().min(Long::compare).orElse(Long.MAX_VALUE);
			mintime = Math.min(mintime, mint);
		}

		StringBuilder b = new StringBuilder();

		// contains the initial timestamp of each signal
		Map<String, Long> mapSignalInitTimestamp = new HashMap<String, Long>();
		// contains the final timestamp of each signal
		Map<String, Long> mapSignalEndTimestamp = new HashMap<String, Long>();

		for (String signal : assignments.keySet()) {
			Long inittimestamp = assignments.get(signal).keySet().stream().min(Long::compare).orElse(0L);
			mapSignalInitTimestamp.put(signal, inittimestamp);
			Long endtimestamp = assignments.get(signal).keySet().stream().max(Long::compare).orElse(0L);
			mapSignalEndTimestamp.put(signal, endtimestamp);
		}

		// a map contining for each signal the ordered set of timestamps
		Map<String, List<Long>> mapSignalTimestamps = new HashMap<String, List<Long>>();
		// contains the last position analyzed for each signal
		Map<String, Integer> signalLastPosition = new HashMap<String, Integer>();
		for (String signal : assignments.keySet()) {
			List<Long> tmp = new ArrayList<Long>(assignments.get(signal).keySet());
			Collections.sort(tmp);
			mapSignalTimestamps.put(signal, tmp);
			signalLastPosition.put(signal, 0);
		}

		int num = 0;

		Set<Long> filteredRepeatedDates=new HashSet<>(dates);

		List<Long> dateList=new ArrayList<Long>(filteredRepeatedDates);

		Collections.sort(dateList);

		for(Long date: dateList) {

			String line = Long.toString(date-mintime);

			for (String signal : assignments.keySet()) {
				Double value = getInterpolatedValue(signal, assignments.get(signal), date-mintime,
						mapSignalInterpolationType.get(signal), mintime, mapSignalInitTimestamp.get(signal),
						mapSignalEndTimestamp.get(signal), signalLastPosition, mapSignalTimestamps.get(signal));
				line += "," + signal + "," + value;
			}
			b.append(line + "\n");
			num = num + 1;
		}
		logsNumberOfEntries.add(num);
		System.out.println("Number of entries: " + num);

		fsa.generateFile(outputFile, b.toString());
	}
	private Double getInterpolatedValue(String signalName, Map<Long, Double> assignments, Long currenttimestamp,
			String interpolationType, Long mintime, Long inittimestamp, Long endtimestamp,
			Map<String, Integer> signalLastPosition, List<Long> timestamps) {

		Long lowertimestamp = timestamps.get(signalLastPosition.get(signalName));
		Long uppertimestamp=Long.MAX_VALUE;
		if(timestamps.size()>1) {
		 uppertimestamp = timestamps.get(signalLastPosition.get(signalName) + 1);
		}
		else {
			uppertimestamp=lowertimestamp;
		}
		if (uppertimestamp < currenttimestamp + inittimestamp && timestamps.size()>signalLastPosition.get(signalName) + 2) {
			signalLastPosition.put(signalName, signalLastPosition.get(signalName) + 1);
			lowertimestamp = timestamps.get(signalLastPosition.get(signalName));
			uppertimestamp = timestamps.get(signalLastPosition.get(signalName) + 1);
		}

		return interpolate(lowertimestamp, currenttimestamp, uppertimestamp ,
				assignments.get(lowertimestamp),   assignments.get(uppertimestamp), interpolationType,mintime);

	}

	private Double interpolate(Long lowertimestamp, Long currenttimestamp, Long uppertimestamp, Double valueLower,
			Double valueUpper, String interpolationType,Long mintime) {

		if (interpolationType.equals("Linear")) {
			if (lowertimestamp == currenttimestamp || valueLower == valueUpper) {
				return valueLower;
			}
			Double value = valueLower + ((currenttimestamp+mintime) - lowertimestamp) * (valueUpper - valueLower)
					/ (uppertimestamp - lowertimestamp);
			return value;
		}
		if (interpolationType.equals("Constant")) {
			Double value = valueLower;
			return value;
		}
		return 0.0;
	}

	/**
	 * Returns a map that for each signal contains the timestamp and the value of
	 * the signal
	 *
	 * @param fsa
	 * @param inputFile
	 * @param signals
	 * @return
	 * @throws FileNotFoundException
	 * @throws ParseException
	 */
	private Map<String, Map<Long, Double>> getAssignments(IFileSystemAccess2 fsa, String inputFile,
			List<String> signals) throws FileNotFoundException, ParseException {
		// For each signal contains the value of the initial observation

		Map<String, Map<Long, Double>> assignments = new HashMap<>();
		Scanner myReader = new Scanner(fsa.readTextFile(inputFile).toString());

		Date lastdate = null;

		while (myReader.hasNextLine()) {
			String line = myReader.nextLine();
			line = line.replaceAll("\\s+", ",");
			if (line.startsWith(",")) {
				line = line.substring(1);
			}
			String[] splitted = line.split(",");


			// if the signal match and was not already in the initialization values
			if (signals.contains(splitted[0])) {

				if (!assignments.containsKey(splitted[0])) {
					assignments.put(splitted[0], new HashMap<>());
				}
				// to put into microseconds
				if(lastdate==null) {
					throw new Error("Initial date null");
				}
				assignments.get(splitted[0]).put((lastdate.getTime() * 1000), Double.parseDouble(splitted[1]));
				lastAssignment = lastdate.getTime() * 1000;// to put into microseconds
				dates.add(lastdate.getTime()* 1000);

			} else {
				if (line != "" && line.length()>20) {
					try {
						SimpleDateFormat formatter = new SimpleDateFormat("yyyy.DDD.HH.mm.ss.SSS");

						lastdate = formatter.parse(line.substring(0,line.length()-3));

					} catch (ParseException e) {

					}
				}
			}

		}
		myReader.close();
		return assignments;
	}

	/**
	 * given an input file it generates the output file by interpolating the signals
	 * contained in the input file
	 *
	 * @param inputFile                  contains the raw traces provided by our
	 *                                   industrial partner
	 * @param outputFile                 the file where the interpolated traces are
	 *                                   written
	 * @param signals                    the set containing the traces of interest
	 * @param sampleStep                 the desired sample step
	 * @param mapSignalInterpolationType a map that contains for each signal the
	 *                                   interpolation type considered for that
	 *                                   signal at the moment the types "Linear" and
	 *                                   "Constant" are considered
	 * @throws ParseException
	 * @throws IOException
	 */
	public void executecheck(IFileSystemAccess2 fsa, String inputFile, String outputFile, List<String> signals,
			Double sampleStep, Map<String, String> mapSignalInterpolationType, String originalTraceFilepath,
			String property, String traceName) throws ParseException, IOException {

		this.isAssigned(fsa, inputFile, signals, traceName, property);

	}

	private void isAssigned(IFileSystemAccess2 fsa, String inputFile, List<String> signals, String trace,
			String requirements) throws FileNotFoundException, ParseException {
		// For each signal contains the value of the initial observation

		Set<String> notAssigned = new HashSet<>(signals);
		Scanner myReader = new Scanner(fsa.readTextFile(inputFile).toString());

		while (myReader.hasNextLine() && !notAssigned.isEmpty()) {
			String line = myReader.nextLine();
			line = line.replaceAll("\\s+", ",");
			if (line.startsWith(",")) {
				line = line.substring(1);
			}
			String[] splitted = line.split(",");

			// if the signal match and was not already in the initialization values
			if (signals.contains(splitted[0])) {

				notAssigned.remove(splitted[0]);

			}

		}
		myReader.close();
		if (!notAssigned.isEmpty()) {
			if (!notPresentSignals.containsKey(trace)) {
				notPresentSignals.put(trace, new HashMap<>());
			}
			notPresentSignals.get(trace).put(requirements, notAssigned);
		}

	}

}
