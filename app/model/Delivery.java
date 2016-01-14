package model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Delivery {

    public Long id;
    public List<DeliveryItem> items;
    public Date deliveryDate;
}
