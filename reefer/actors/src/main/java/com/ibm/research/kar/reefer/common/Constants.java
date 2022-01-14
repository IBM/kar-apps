/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.research.kar.reefer.common;

public class Constants {
   public static final String REEFER_STATS_MAP_KEY="reefer-stats-map";
   public static final String TOTAL_BOOKED_KEY="totalBooked";
   public static final String TOTAL_SPOILT_KEY="totalSpoilt";
   public static final String TOTAL_INTRANSIT_KEY="totalInTransit";
   public static final String TOTAL_ONMAINTENANCE_KEY="totalOnMaintenance";

   public static final String ACTIVE_ORDERS_KEY="active-orders";
   public static final String ACTIVE_VOYAGES_KEY="active-voyages";
   public static final String BOOKED_ORDERS_KEY="booked-orders";
   public static final String SPOILT_ORDERS_KEY="spoilt-orders";
   public static final String SPOILT_REEFER_KEY="spoilt-reefer";
   public static final String REST_ORDERS_KEY="rest-order-map";
   public static final String VOYAGE_ID_KEY="voyageId";
   public static final String VOYAGE_ARRIVAL_DATE_KEY="voyage-arrival-date";
   public static final String VOYAGE_ORDERS_KEY="voyage-orders";
   public static final String VOYAGE_REEFERS_KEY="voyage-reefers";
   public static final String VOYAGE_EMPTY_REEFERS_KEY="voyage-empty-reefers";
   public static final String VOYAGE_INFO_KEY="voyage-info";
   public static final String VOYAGE_STATUS_KEY="voyage-status";
   public static final String VOYAGE_DAYSATSEA_KEY="daysAtSea";
   public static final String VOYAGE_FREE_CAPACITY_KEY="free-capacity";
   public static final int DEPOT_TARGET_TYPE=1;
   public static final int VOYAGE_TARGET_TYPE=2;
   public static final String REEFER_PROVISIONER_STATE_KEY="provisionerState";
   public static final String REEFER_FLEET_SIZE_KEY="REEFER_FLEET_SIZE";

   public static final String TOTAL_REEFER_COUNT_KEY="total";
   public static final String REEFER_ID_KEY="reeferId";
   public static final String REEFER_REPLACEMENT_ID_KEY="reeferReplacementId";
   public static final String REEFER_STATE_KEY="reeferState";
   public static final String REEFER_MAP_KEY="reefer-map";
   public static final String REEFERS_KEY="reefers";
   public static final String REEFERS_MAINTENANCE_DATE="maintenanceDate";
   public static final String REEFER_METRICS_KEY="reefer-metrics";
   public static final String REEFER_METRICS_MAP_KEY="reefer-metrics-map";
   public static final String REEFERS_IN_TRANSIT_COUNT_KEY="reefer-in-transit";
   public static final String SPOILT_REEFERS_KEY="spoilt-reefers";

   public static final String ANOMALY_TARGET_KEY="anomaly-target";
   public static final String ANOMALY_KEY="anomaly";
   public static final String ANOMALY_ORIGIN_KEY="anomaly-origin";
   public static final String ANOMALY_TARGET_TYPE_KEY="anomaly-target_type";
   public static final String ORDER_ID_KEY="orderId";
   public static final String ORDERS_KEY="orders";
   public static final String ORDER_REEFERS_KEY="order-reefers";
   public static final String ORDER_METRICS_KEY="order-metrics";
   public static final String ORDER_KEY="order";
   public static final String ORDER_DATE_KEY="date";
   public static final String ORDER_SPOILT_KEY="spoilt";
   public static final String ORDER_ALREADY_SPOILT_KEY="spoilt-already";
   public static final String ORDER_STATUS_KEY="order-status";
   public static final String ORDER_PRODUCT_KEY="product";
   public static final String ORDER_PRODUCT_QTY_KEY="productQty";
   public static final String ORDER_CUSTOMER_ID_KEY="customerId";
   public static final String CURRENT_DATE_KEY="current-date";
   public static final String SCHEDULE_BASE_DATE_KEY="schedule-base-date";
   public static final String SCHEDULE_END_DATE_KEY="schedule-end-date";
   public static final String ORDER_BOOKING_STATUS_KEY="booking-status";
   public static final String VOYAGE_ORDER_MAP_KEY="voyage-order-map";

   // each reefer can hold up to 1000 product units. Simplification is that
   // each product unit has the same size.
   public static final int REEFER_CAPACITY=1000;
   public static final int REEFER_DAYS_ON_MAINTENANCE=2;
   public static final String DEPOTS_KEY="depots";
   public static final String DEPOT_KEY="depot";
   public static final String SHARDS_KEY="shards";
   public static final String SHARD_KEY="shard";
   public static final String DEPOT_SIZE_KEY="depot-size";
   public static final String TARGETS_KEY="targets";
   public static final String TARGET_KEY="target";
   public static final String TARGET_MAP_KEY="target-enum-map";
   public static final String TARGET_TYPE_KEY="target-type";
   public static final String ON_MAINTENANCE_PROVISIONER_LIST="on-maintenance-list";
   public static final String DATE_KEY="date";
   public static final String STATUS_KEY="status";
   public static final String IS_ORIGIN_KEY="is-origin";
   public static final String OK="OK";
   public static final String FAILED="Failed";
   public static final String ERROR="error";

   public static final String SIMSERVICE="simservice";
   public static final String REEFERSERVICE="reeferservice";

   public static final String REEFER_DEPOT_SUFFIX="ReeferDepot";
}