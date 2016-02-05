/**
 * Copyright (C) ${year}
 * by 52 North Initiative for Geospatial Open Source Software GmbH
 *
 * Contact: Andreas Wytzisk
 * 52 North Initiative for Geospatial Open Source Software GmbH
 * Martin-Luther-King-Weg 24
 * 48155 Muenster, Germany
 * info@52north.org
 *
 * This program is free software; you can redistribute and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation.
 *
 * This program is distributed WITHOUT ANY WARRANTY; even without the implied
 * WARRANTY OF MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program (see gnu-gpl v2.txt). If not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA or
 * visit the Free Software Foundation web page, http://www.fsf.org.
 */
package org.envirocar.wps;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.util.Date;

import org.envirocar.wps.util.CsvFileWriter;
import org.envirocar.wps.util.EnviroCarFeatureParser;
import org.envirocar.wps.util.EnviroCarWpsConstants;
import org.envirocar.wps.util.EnviroCarWpsConstants.FeatureProperties;
import org.envirocar.wps.util.TimeWindowStats;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.n52.wps.algorithm.annotation.Algorithm;
import org.n52.wps.algorithm.annotation.ComplexDataInput;
import org.n52.wps.algorithm.annotation.ComplexDataOutput;
import org.n52.wps.algorithm.annotation.Execute;
import org.n52.wps.algorithm.annotation.LiteralDataInput;
import org.n52.wps.io.data.GenericFileData;
import org.n52.wps.io.data.binding.complex.GTVectorDataBinding;
import org.n52.wps.io.data.binding.complex.GenericFileDataBinding;
import org.n52.wps.io.data.binding.complex.JTSGeometryBinding;
import org.n52.wps.server.AbstractAnnotatedAlgorithm;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Just a simple Algorithm
 * 
 * @author: Julius Wittkopp
 */
@Algorithm(version = "1.0.0")
public class StatsForPOI extends AbstractAnnotatedAlgorithm {
	
    private static Logger LOGGER = LoggerFactory.getLogger(StatsForPOI.class);
    
    
    private static final int EPSG_CODE_GPS = 4326;
    private static final int EPSG_CODE_GK3 = 31467;

	private SimpleFeatureTypeBuilder typeBuilder;
	
    public StatsForPOI() {
        super();
    }
    
	
    private GenericFileData result;
    private double bufferSize;
    private Geometry pointOfInterest;
    private Integer[] time;
    
   public static int numberOfTracks = 0;
   public static int amountOfPoints= 0;
   public static int TotalAmountOfPoints= 0;
    
    @ComplexDataOutput(identifier = "result", binding = GenericFileDataBinding.class)
    public GenericFileData getResult() {
        return result;
    }

    @LiteralDataInput(identifier = "bufferSize", abstrakt="Specify the size of the buffer that is used to identify stops around a traffic light.")
    public void setBufferSize(double bufferSize) {
        this.bufferSize = bufferSize;
    }
    
    
    @ComplexDataInput(identifier = "pointOfInterest", binding = JTSGeometryBinding.class)
    public void setData(Geometry data) {
        this.pointOfInterest = data;
    }
	
    @Execute
	public void simpleAlgorithm() throws Exception {
       simpleAlgorithm(this.pointOfInterest,this.bufferSize);
	}	
    
    /**
     * computes stops at point 
     * 
     * @param pointOfInterest
     * @param bufferSize
     * @param day
     * @throws Exception
     */
	
	public void simpleAlgorithm(Geometry pointOfInterest, double bufferSize) throws Exception {
		
		Point inputPoint = (Point)pointOfInterest;
		System.out.println("Computing amount of points ("+inputPoint.getX()+", "+inputPoint.getY()+") with buffer size "+bufferSize+" m.");
		LOGGER.debug("Computing amount of points ("+inputPoint.getX()+", "+inputPoint.getY()+") with buffer size "+bufferSize+" m.");
		pointOfInterest.setSRID(EPSG_CODE_GPS);
    	
    	//project to GK3 to compute buffer in meters
    	Geometry gkPoint = projectToGK3(pointOfInterest);
    	Geometry buffer = gkPoint.buffer(bufferSize);
    	
    	//re-transform to WGS84 coordinates for BBOX filter in query of tracks from Envirocar server
    	Geometry envelope = transformToWGS(buffer.getEnvelope());
    	Coordinate[] coords = envelope.getCoordinates();
    	double minx = coords[0].y;
    	double maxx = coords[2].y;
    	double miny = coords[0].x;
    	double maxy = coords[2].x;
    	System.out.println(""+ minx + " "+ miny + " "+ maxx +" " +maxy);
    	
    	//query tracks from EnviroCar server
    	String bboxQuery = "?bbox="+minx+","+miny+","+maxx+","+maxy;
		String queryUrl = EnviroCarWpsConstants.ENV_SERVER_URL+"/tracks"+bboxQuery;
		System.out.println(""+ queryUrl);
    	URL u = new URL(queryUrl);
        InputStream in = u.openStream();
		ObjectMapper objMapper = new ObjectMapper();
		Map<?, ?> map = objMapper.readValue(in, Map.class);
		ArrayList<?> trackIDs = null;

		//init result properties
		
		//create spatial filter for checking whether measurements of tracks are within buffer around POI
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		Coordinate[] coordsFlipped = flipCoordinates(envelope.getCoordinates());
		GeometryFactory geomFac = new GeometryFactory();
		Polygon bufferFlipped = geomFac.createPolygon(geomFac.createLinearRing(coordsFlipped),null);
		Filter filter = ff.within(ff.property(FeatureProperties.GEOMETRY),ff.literal(bufferFlipped));
		
		SetMultimap<String, Object> finalStatistics = HashMultimap.create();
		
		//iterate over track IDs
		for (Object o : map.keySet()) {
			Object entry = map.get(o);
			if (o.equals("tracks")) {
				trackIDs = (ArrayList<?>) entry;
				numberOfTracks = trackIDs.size();
				String trackDate = "";
				System.out.println("trackIDSize "+ numberOfTracks);
				
				
				//for each track query the measurements  
				for (Object item:trackIDs){
					
					//List<String> parameterValues = new ArrayList<String>();
					int parameterValues = 0 ;
					int amountOfPoints = 0;
					
					String trackID = (String) ((LinkedHashMap)item).get("id");
					System.out.println("Getting features for track with ID: " + trackID);
					LOGGER.debug("Getting features for track with ID: " + trackID);
					URL trackUrl = new URL(EnviroCarWpsConstants.ENV_SERVER_URL+"/tracks/"+trackID+"/measurements"+bboxQuery);
					System.out.println(" "+ trackUrl);
					EnviroCarFeatureParser parser = new EnviroCarFeatureParser();
					SimpleFeatureCollection trackFc = parser.createFeaturesFromJSON(trackUrl);
					SimpleFeatureIterator featIter = trackFc.features();
					
					//parameterValues.clear();
					try {
						
						//iterate over Measurements of track
						while (featIter.hasNext()){

							SimpleFeature feat = featIter.next();
							int trackTime;
							//check if attribute time exists
							if (feat.getAttribute("Speed (km/h)")!=null){	
								//Setting date format
								SimpleDateFormat dateFormatInput=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
								//Setting date format to "EEEE" - output name of weekday 
								SimpleDateFormat dateFormatOutput=new SimpleDateFormat("EEEE"); 
								//Setting new date format for Time (HH - Hour) 
								SimpleDateFormat timeFormatOutput=new SimpleDateFormat("HH");
								//receive string time 
								trackDate = ((String)feat.getAttribute("time"));
								//Parse String to Date
								Date dt1=dateFormatInput.parse(trackDate);
								// Save name of weekday when track has been created (e.g. Monday) as String trackDay 
								trackDate=dateFormatOutput.format(dt1);
								//trackTime saves the Hour when track has been created (e.g. 15)
								trackTime= Integer.parseInt(timeFormatOutput.format(dt1));	
								//save trackDay and trackTime into Hashmap date
								trackDate += "_"+ trackTime;
								System.out.println("Day: "+ trackDate);
															
								//String speed = (String)feat.getAttribute("Speed (km/h)");
								double speed = Double.parseDouble((String)feat.getAttribute("Speed (km/h)"));
								amountOfPoints++;
								// parameterValues.add(speed);
								parameterValues = (int) (parameterValues + speed);
								TotalAmountOfPoints++;
							} else{
								amountOfPoints = 1;
								break;
							}
	
						}
						System.out.println("berechnung: "+ parameterValues+ " "+ amountOfPoints+ " "+ (parameterValues)/amountOfPoints);
						parameterValues = (parameterValues)/amountOfPoints;
						TimeWindowStats tws1 = new TimeWindowStats(parameterValues);
						finalStatistics.put(trackDate, tws1);
						System.out.println(" "+ finalStatistics);
			
					} catch (Exception e){
						LOGGER.debug("Error while extracting amount of points in buffer: "+e.getLocalizedMessage());
						throw(e);
					}
					finally{
						featIter.close();
					}
//					result = new GenericFileData (CsvFileWriter.writeCsvFile("erstecsv", finalStatistics),"application/csv");
//					System.out.println("result" + result);	

				}
				result = new GenericFileData (CsvFileWriter.writeCsvFile("erstecsv", finalStatistics),"application/csv");		
			} else{
				break;
			}

		}
		
		//set up feature type for feature that is returned
		String uuid = UUID.randomUUID().toString().substring(0, 5);
		String namespace = "http://www.52north.org/" + uuid;
		SimpleFeatureType sft = null;
		SimpleFeatureBuilder sfb = null;
		SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
		typeBuilder.setCRS(CRS.decode("EPSG:4326"));
		typeBuilder.setNamespaceURI(namespace);
		Name nameType = new NameImpl(namespace, "Feature-" + uuid);
		typeBuilder.setName(nameType);
		String featID = "feature-"+UUID.randomUUID().toString().substring(0, 5);
		typeBuilder.add("geometry", Point.class);
		typeBuilder.add("id", String.class);
		typeBuilder.add("totalAmountOfPoints", String.class);
		typeBuilder.add("totalNumberOfTracks", String.class);
		sft = typeBuilder.buildFeatureType();
		sfb = new SimpleFeatureBuilder(sft);
		
		
		//set feature properties
		sfb.set("geometry",pointOfInterest);
		sfb.set("id", featID);
		sfb.set("totalAmountOfPoints", amountOfPoints);
		sfb.set("totalNumberOfTracks", numberOfTracks);
		System.out.println("Number of Measurements: " + amountOfPoints + "; total number of tracks: "+ numberOfTracks);
		LOGGER.debug("Number of points: " + amountOfPoints + "; total number of tracks: "+ numberOfTracks);
		
		//create feature collection that is returned
		List<SimpleFeature> simpleFeatureList = new ArrayList<SimpleFeature>();
		simpleFeatureList.add(sfb.buildFeature(featID));
		System.out.println("funktioniert2 ");
		
	}


	private static Geometry projectToGK3(Geometry geom) throws Exception{
    	Geometry result = null;
    	Coordinate[] coordsNew = flipCoordinates(geom.getCoordinates());
    	if (geom instanceof Point && coordsNew.length==1){
    		geom = new GeometryFactory().createPoint(coordsNew[0]);
    	}
    	CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:"+EPSG_CODE_GPS);
    	CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:"+EPSG_CODE_GK3);
    	MathTransform trans = CRS.findMathTransform(sourceCRS, targetCRS);
    	result = JTS.transform(geom,trans);
    	return result;
    }
    
    /**
     * helper method for transformation from Gauss-Krueger-3 projection to WGS84
     * 
     * @param geom
     * 			geometry that should be projected
     * @return 
     * 			projected geometry
     * @throws Exception
     * 			if projection fails
     */
    private static Geometry transformToWGS(Geometry geom) throws NoSuchAuthorityCodeException, FactoryException, MismatchedDimensionException, TransformException{
    	Geometry result = null;
    	CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:"+EPSG_CODE_GK3);
    	CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:"+EPSG_CODE_GPS);
    	MathTransform trans = CRS.findMathTransform(sourceCRS, targetCRS);
    	result = JTS.transform(geom,trans);
    	if (result instanceof Polygon){
    		Polygon poly = (Polygon)result;
    		Coordinate[] coords = flipCoordinates(poly.getExteriorRing().getCoordinates());
    		GeometryFactory geomFactory = new GeometryFactory();
    		result  = geomFactory.createPolygon(geomFactory.createLinearRing(coords),null);
    	}
    	return result;
    }
    
    private static Coordinate[] flipCoordinates(Coordinate[] coords){
    	Coordinate[] coordsNew = new Coordinate[coords.length];
    	for (int i = 0; i<coords.length; i++){
    		coordsNew[i]=new Coordinate(coords[i].y,coords[i].x);
    	}
    	return coordsNew;
    }
}