package com.example.order.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.order.dao.OrderRepository;
import com.example.order.domain.dto.OrderRequestDTO;
import com.example.order.domain.dto.OrderResponseDTO;
import com.example.order.domain.dto.ProductResponseDTO;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final ProductOpenFeignService   productOpenFeignService ;
    private final OrderRepository           orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /*
    1. Client(path: /order/create) -> 주문요청
    2. orderFeignKafkaCreate() 실행
    3. Feign 상품 조회(동기방식)
    4. 재고 여부 확인
    5. kafka 토픽(KafkaTemplate.send())을 발행
    6. kafka broker에 메시지 저장
    7. product-service 에서 수신(KafkaListener)하여 stockConsumer() 호출
    8. 재고감소구현
    */
   @CircuitBreaker(name = "productService" , fallbackMethod = "fallbackProductService" )
    public OrderResponseDTO orderFeignKafkaCreate(OrderRequestDTO request, String email){
        System.out.println(">>>> order service orderFeignKafkaCreate ");
        System.out.println(">>>> 재고 유무 판단을 위해서 product-service Feign 통신");
        ProductResponseDTO response = productOpenFeignService.getProductId(request.getProductId(), email);
        System.out.println(">>>> Feign 통신결과 수량확인 : "+ response.getStockQty());
        if(response.getStockQty() < request.getQty()) {
            throw new RuntimeException("재고부족");
        } else {
            System.out.println(">>>> order service kafka 토픽 발행");
            kafkaTemplate.send("update-stock-topic", request);
        }
        // 주문저장~~~~
        return OrderResponseDTO.fromEntity(orderRepository.save(request.toEntity(email)));
        
    }

    public OrderResponseDTO fallbackProductService(OrderRequestDTO request,
                                                    String email,
                                                    Throwable t){
        throw new RuntimeException("서비스 지연으로 에러발생됨...다시 시도 해 주세요");
    }
}