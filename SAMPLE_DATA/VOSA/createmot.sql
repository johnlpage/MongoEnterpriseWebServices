-- This loads the data into MySQL
-- I do have PG ones too.

SET GLOBAL local_infile=1;
DROP DATABASE IF EXISTS MOT;

CREATE DATABASE MOT;

use MOT;

CREATE TABLE TESTRESULT (
	TESTID BIGINT
	,VEHICLEID BIGINT
	,TESTDATE DATE
	,TESTCLASSID VARCHAR(2)
	,TESTTYPE VARCHAR(2)
	,TESTRESULT VARCHAR(5)
	,TESTMILEAGE BIGINT
	,POSTCODEREGION VARCHAR(2)
	,MAKE VARCHAR(50)
	,MODEL VARCHAR(50)
	,COLOUR VARCHAR(16)
	,FUELTYPE VARCHAR(2)
	,CYLCPCTY BIGINT
	,FIRSTUSEDATE DATE
	,PRIMARY KEY (TESTID)
	)
;

CREATE INDEX TESTRESULT_IDX1 ON TESTRESULT (TESTDATE, TESTTYPE, TESTRESULT, TESTCLASSID);

CREATE TABLE TESTITEM (
	TESTID BIGINT
	,RFRID INT
	,RFRTYPE VARCHAR(1)
	,LOCATIONID INT
	,DMARK VARCHAR(1)
	,PRIMARY KEY(TESTID,RFRID,LOCATIONID)
	);


CREATE INDEX TESTITEM_IDX1 ON TESTITEM (RFRID);


CREATE TABLE TESTITEM_DETAIL (
	RFRID INT
	,TESTCLASSID VARCHAR(2)
	,TSTITMID INT
	,MINORITEM VARCHAR(1)
	,RFRDESC VARCHAR(250)
	,RFRLOCMARKER VARCHAR(1)
	,RFRINSPMANDESC TEXT
	,RFRADVISORYTEXT VARCHAR(250)
	,TSTITMSETSECID INT
	,PRIMARY KEY (RFRID, TESTCLASSID)
	)
;


CREATE INDEX TESTITEM_DETAIL_IDX1 ON TESTITEM_DETAIL (TSTITMID, TESTCLASSID);

CREATE INDEX TESTITEM_DETAIL_IDX2 ON TESTITEM_DETAIL (TSTITMSETSECID, TESTCLASSID);

CREATE TABLE TESTITEM_GROUP (
	TSTITMID INT
	,TESTCLASSID VARCHAR(2)
	,PARENTID INT
	,TSTITMSETSECID INT
	,ITEMNAME VARCHAR(100)
	,PRIMARY KEY (TSTITMID, TESTCLASSID)
	)
;

CREATE INDEX TESTITEM_GROUP_IDX1 ON TESTITEM_GROUP  (PARENTID, TESTCLASSID);
CREATE INDEX TESTITEM_GROUP_IDX2 ON TESTITEM_GROUP  (TSTITMSETSECID, TESTCLASSID);

CREATE TABLE FAILURE_LOCATION (
	FAILURELOCATIONID INT
	,LAT VARCHAR(20)
	,LONGITUDINAL VARCHAR(20)
	,VERTICAL VARCHAR(20)
	,PRIMARY KEY (FAILURELOCATIONID)
	)
;


CREATE TABLE TEST_OUTCOME (
  RESULTCODE VARCHAR(6),
  RESULT VARCHAR(40),
  PRIMARY KEY (RESULTCODE)
);

CREATE TABLE TEST_TYPES (
  TYPECODE VARCHAR(6),
  TESTTYPE VARCHAR(40),
  PRIMARY KEY (TYPECODE)
);

CREATE TABLE FUEL_TYPES (
  TYPECODE VARCHAR(6),
  FUEL_TYPE VARCHAR(40),
  PRIMARY KEY (TYPECODE)
);


LOAD DATA  LOCAL INFILE 'test_result_2021.csv' 
INTO TABLE TESTRESULT
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
;


LOAD DATA LOCAL INFILE 'test_item_2021.csv' 
INTO TABLE TESTITEM
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
;

LOAD DATA LOCAL INFILE 'item_detail.csv' 
INTO TABLE TESTITEM_DETAIL
FIELDS TERMINATED BY '|'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
;

LOAD DATA  LOCAL INFILE 'item_group.csv' 
INTO TABLE TESTITEM_GROUP
FIELDS TERMINATED BY '|'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
;



LOAD DATA  LOCAL INFILE 'mdr_rfr_location.csv' 
INTO TABLE FAILURE_LOCATION
FIELDS TERMINATED BY '|'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
;


LOAD DATA LOCAL  INFILE 'mdr_test_type.csv' 
INTO TABLE TEST_TYPES
FIELDS TERMINATED BY '|'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
;



LOAD DATA LOCAL INFILE 'mdr_fuel_types.csv' 
INTO TABLE FUEL_TYPES
FIELDS TERMINATED BY '|'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
;


LOAD DATA LOCAL  INFILE 'mdr_test_outcome.csv' 
INTO TABLE TEST_OUTCOME
FIELDS TERMINATED BY '|'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
;

CREATE INDEX VEHIID_IDX ON TESTRESULT(VEHICLEID);




