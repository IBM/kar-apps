package com.ibm.research.kar.reefer.common;

public class Constants {
   public static final String TOTAL_BOOKED_KEY="totalBooked";
   public static final String TOTAL_SPOILT_KEY="totalSpoilt";
   public static final String TOTAL_INTRANSIT_KEY="totalInTransit";
   public static final String TOTAL_ONMAINTENANCE_KEY="totalOnMaintenance";

   public static final String ACTIVE_ORDERS_KEY="active-orders";
   public static final String BOOKED_ORDERS_KEY="booked-orders";
   public static final String SPOILT_ORDERS_KEY="spoilt-orders";

   public static final String VOYAGE_ID_KEY="voyageId";
   public static final String VOYAGE_ORDERS_KEY="voyage-orders";
   public static final String VOYAGE_INFO_KEY="voyage-info";
   public static final String VOYAGE_STATUS_KEY="voyage-status";
   public static final String VOYAGE_DAYSATSEA_KEY="daysAtSea";

   public static final String REEFER_PROVISIONER_STATE_KEY="provisionerState";

   public static final String TOTAL_REEFER_COUNT_KEY="total";
   public static final String REEFER_ID_KEY="reeferId";
   public static final String REEFER_REPLACEMENT_ID_KEY="reeferReplacementId";
   public static final String REEFER_STATE_KEY="reeferState";
   public static final String REEFER_MAP_KEY="reefer-map";
   public static final String REEFERS_KEY="reefers";
   public static final String REEFERS_MAINTENANCE_DATE="maintenanceDate";

   public static final String ORDER_ID_KEY="orderId";
   public static final String ORDER_STATUS_KEY="order-status";
   public static final String ORDER_KEY="order";

   public static final String VOYAGE_ORDER_MAP_KEY="voyage-order-map";

   // each reefer can hold up to 1000 product units. Simplifaction is that
   // each product unit has the same size.
   public static final int REEFER_CAPACITY=1000;
   public static final int REEFER_DAYS_ON_MAINTENANCE=2;

   public static final String ON_MAINTENANCE_PROVISIONER_LIST="on-maintenance-list";
   public static final String DATE_KEY="date";
   public static final String STATUS_KEY="status";

   public static final String OK="OK";
   public static final String FAILED="Failed";
   public static final String ERROR="error";
}