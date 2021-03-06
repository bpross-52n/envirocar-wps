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

package org.envirocar.wps.util;


import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;

import java.util.HashMap;

/**
 * class represents statistics of eC measurements for a specific time window
 * 
 * @author Julius Wittkopp
 *
 */
public class TimeWindowStats{
	
//	List <String> parameterValues;
//	//String parameterName;
//	
//	
//	public TimeWindowStats(List<String> parameterValues){
//	     this.parameterValues = parameterValues;
//	     
//	}
//	
//	public List<String> getparameterValues(){
//        return this.parameterValues;
//    }

	
	int parameterValues;
	
	public TimeWindowStats(int parameterValues){
	     this.parameterValues = parameterValues;
	     
	}

	
	public String toString(){
		return " " + parameterValues;
	}	
}
