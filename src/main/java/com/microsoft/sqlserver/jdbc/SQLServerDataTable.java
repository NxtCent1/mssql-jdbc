//---------------------------------------------------------------------------------------------------------------------------------
// File: SQLServerDataTable.java
//
//
// Microsoft JDBC Driver for SQL Server
// Copyright(c) Microsoft Corporation
// All rights reserved.
// MIT License
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files(the ""Software""), 
//  to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
//  and / or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions :
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
// THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS 
//  IN THE SOFTWARE.
//---------------------------------------------------------------------------------------------------------------------------------
 
 
package com.microsoft.sqlserver.jdbc;

import java.util.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Map.Entry;
import java.time.*;

public final class SQLServerDataTable {

	int rowCount = 0;
	int columnCount = 0;
	Map<Integer, SQLServerDataColumn> columnMetadata = null;
	Map<Integer,Object[]> rows = null;

	/**
	 * The constant in the Java programming language, sometimes referred to as a type code,
	 * that identifies the type TVP.
	 */
	// Name used in CREATE TYPE 
	public SQLServerDataTable() throws SQLServerException
	{
		columnMetadata = new LinkedHashMap<Integer, SQLServerDataColumn>();
		rows = new HashMap<Integer,Object[]>();
	}

	public synchronized void clear()
	{
		rowCount = 0;
		columnCount = 0;
		columnMetadata.clear();
		rows.clear();		
	}

	public synchronized Iterator<Entry<Integer,Object[]>> getIterator()
	{
		if ((null != rows) && (null != rows.entrySet()))
		{
			return rows.entrySet().iterator();
		}
		return null;
	}

	public synchronized void addColumnMetadata(String columnName, int sqlType) throws SQLServerException
	{
		//column names must be unique
		Util.checkDuplicateColumnName(columnName, columnMetadata);
		columnMetadata.put(columnCount++, new SQLServerDataColumn(columnName,sqlType));
	}

	public synchronized void addColumnMetadata(SQLServerDataColumn column) throws SQLServerException
	{
		//column names must be unique
		Util.checkDuplicateColumnName(column.columnName, columnMetadata);
		columnMetadata.put(columnCount++, column);
	}

	public synchronized void addRow(Object... values) throws SQLServerException
	{
		try
		{
			int columnCount = columnMetadata.size();

			if ( (null != values) && values.length > columnCount)
			{
				MessageFormat form = new MessageFormat(SQLServerException.getErrString("R_moreDataInRowThanColumnInTVP"));
				Object[] msgArgs = {};
				throw new SQLServerException(null , form.format(msgArgs) , null, 0 , false);   
			}

			Iterator<Entry<Integer, SQLServerDataColumn>> columnsIterator = columnMetadata.entrySet().iterator();
			Object[] rowValues = new Object[columnCount];
			int currentColumn = 0;
			while(columnsIterator.hasNext())
			{
				Object val = null;
				boolean bValueNull ;
				int nValueLen ;

				if((null != values) && (currentColumn < values.length) && (null != values[currentColumn]))
					val = (null == values[currentColumn]) ? null : values[currentColumn] ;
				currentColumn++;
				Map.Entry<Integer, SQLServerDataColumn> pair = (Map.Entry<Integer, SQLServerDataColumn>)columnsIterator.next();
				SQLServerDataColumn currentColumnMetadata = pair.getValue();
				JDBCType jdbcType = JDBCType.of(pair.getValue().javaSqlType);

				boolean isColumnMetadataUpdated = false;
					switch (jdbcType)
					{
					case BIGINT:
						rowValues[pair.getKey()] = (null == val) ? null : Long.parseLong(val.toString());
						break;

					case BIT:
						rowValues[pair.getKey()] = (null == val) ? null : Boolean.parseBoolean(val.toString());
						break;

					case INTEGER:
						rowValues[pair.getKey()] = (null == val) ? null : Integer.parseInt(val.toString());
						break;

					case SMALLINT:
					case TINYINT:
						rowValues[pair.getKey()] = (null == val) ? null : Short.parseShort(val.toString());
						break;

					case DECIMAL:
					case NUMERIC:
						BigDecimal bd = null;
						if(null != val)
						{
							bd = new BigDecimal(val.toString());
							if (bd.scale() > currentColumnMetadata.scale)
							{
								currentColumnMetadata.scale = bd.scale();
								isColumnMetadataUpdated = true;
							}
							if (bd.precision() > currentColumnMetadata.precision)
							{
								currentColumnMetadata.precision = bd.precision();
								isColumnMetadataUpdated = true;
							}
							if(isColumnMetadataUpdated)
								columnMetadata.put(pair.getKey(), currentColumnMetadata);
						}
						rowValues[pair.getKey()] = bd;
						break;

					case DOUBLE:
						rowValues[pair.getKey()] = (null == val) ? null : Double.parseDouble(val.toString());
						break;

					case FLOAT:
					case REAL:
						rowValues[pair.getKey()] = (null == val) ? null : Float.parseFloat(val.toString());
						break;

					case TIMESTAMP_WITH_TIMEZONE:
					case TIME_WITH_TIMEZONE:
						DriverJDBCVersion.checkSupportsJDBC42();
					case DATE:
					case TIME:
					case TIMESTAMP:
					case DATETIMEOFFSET:
						// Sending temporal types as string. Error from database is thrown if parsing fails
						// no need to send precision for temporal types, string literal will never exceed DataTypes.SHORT_VARTYPE_MAX_BYTES

						if (null == val)
							rowValues[pair.getKey()] = null;
						//java.sql.Date, java.sql.Time and java.sql.Timestamp are subclass of java.util.Date
						else if (val instanceof java.util.Date)
							rowValues[pair.getKey()] = ((java.util.Date) val).toString();
						else if(val instanceof microsoft.sql.DateTimeOffset)
							rowValues[pair.getKey()] = ((microsoft.sql.DateTimeOffset) val).toString();
						else if(val instanceof OffsetDateTime)
							rowValues[pair.getKey()] = ((OffsetDateTime) val).toString();
						else if(val instanceof OffsetTime)
							rowValues[pair.getKey()] = ((OffsetTime) val).toString();
						else
							rowValues[pair.getKey()] = (null == val) ? null : (String) val;
						break;

					case BINARY:
					case VARBINARY:
						bValueNull = (null == val);
						nValueLen = bValueNull ? 0 : ((byte[]) val).length;

						if (nValueLen > currentColumnMetadata.precision)
						{
							currentColumnMetadata.precision = nValueLen;
							columnMetadata.put(pair.getKey(), currentColumnMetadata);
						}
						rowValues[pair.getKey()] = (bValueNull) ? null : (byte[]) val;

						break;

					case CHAR:
						if(val instanceof UUID && (val != null))
							val = ((UUID)val).toString();
					case VARCHAR:
					case NCHAR:
					case NVARCHAR:
						bValueNull = (null == val);
						nValueLen = bValueNull ? 0 : (2 * ((String)val).length());

						if (nValueLen > currentColumnMetadata.precision)
						{
							currentColumnMetadata.precision = nValueLen;
							columnMetadata.put(pair.getKey(), currentColumnMetadata);
						}
						rowValues[pair.getKey()] = (bValueNull) ? null : (String) val;
						break;

					default:
						MessageFormat form = new MessageFormat(SQLServerException.getErrString("R_unsupportedDataTypeTVP"));
						Object[] msgArgs = {jdbcType};
						throw new SQLServerException(null , form.format(msgArgs) , null, 0 , false);   
					}
				}
			rows.put(rowCount++, rowValues);
		}
		catch(NumberFormatException e)
		{
			throw new SQLServerException(SQLServerException.getErrString("R_TVPInvalidColumnValue"), e);
		}
		catch(ClassCastException e)
		{
			throw new SQLServerException(SQLServerException.getErrString("R_TVPInvalidColumnValue"), e);
		}

		}

		public synchronized Map<Integer, SQLServerDataColumn> getColumnMetadata()
		{
			return columnMetadata;
		}	
	}
