package com.ibm.research.kar.reefer.common;

import com.ibm.research.kar.reefer.actors.AnomalyManagerActor;

public class Tester {
   public static void main(String[] arg) {
      String reeferTargets =   "0|Tanger MedReeferDepot|1,1|Tanger MedReeferDepot|1,2|Tanger MedReeferDepot|1,3|Tanger MedReeferDepot|1,4|Tanger MedReeferDepot|1,5|Tanger MedReeferDepot|1,6|Tanger MedReeferDepot|1,7|Tanger MedReeferDepot|1,8|Tanger MedReeferDepot|1,9|Tanger MedReeferDepot|1";


      String[] targets = reeferTargets.split(",");
      for ( String target : targets ) {
         String[] props = target.split("\\|");
         if ( props == null || props.length == 0 || props[0].trim().length() == 0 ) {
            continue;
         }
         try {
            AnomalyManagerActor.ReeferLocation loc =  new AnomalyManagerActor.ReeferLocation(Integer.valueOf(props[0]), props[1], Integer.valueOf(props[2]));
         } catch( Exception e) {
            e.printStackTrace();
            System.out.println("AnomalyManagerActor.instantiateReeferTargetMap() ******* props[0]:"+props[0]+" props[1]:"+props[1]+" props[2]:"+props[2]);
         }

      }
   }
}
