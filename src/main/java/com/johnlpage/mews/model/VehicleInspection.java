package com.johnlpage.mews.model;

import com.fasterxml.jackson.annotation.*;

import java.util.Date;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;


/* Replace @Data with this to make an Immutable model
 * which is a little more efficient but no setters just a builder
 * This also impact the controller and fuzzer and JsonLoaderService -
 * changes there are commented
 *
 *  @Builder(toBuilder = true)
 *  @Jacksonized
 *  @Value
 */


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "inspections")
public class VehicleInspection  {

  @Id Long testid;
  @Version Long version;
  Long vehicleid;
  Date testdate;
  String testclass;
  String testtype;
  String testresult;
  Long testmileage;
  String postcode;
  String make;
  @JsonProperty("model")
  @Field("vehicle_model")
  String vehicleModel;
  String colour;
  String files;
  Long capacity;
  Date firstusedate;
  /* Use this to flag from the JSON we want to remove the record */
  @JsonIgnore @Transient @DeleteFlag Boolean deleted;


  /** Use this to capture any fields not captured explicitly
   * As MongoDB's flexibility makes this easy
   * */
  @JsonAnySetter
  @Singular("payload")
  Map<String, Object> payload;


}
