package com.ibm.research.kar.reeferserver.repository;
import com.ibm.research.kar.reefer.model.OrderDTO;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface OrderRepository extends PagingAndSortingRepository<OrderDTO, Long> {
    
}