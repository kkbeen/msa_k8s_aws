package com.example.product.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.example.product.dao.ProductRepository;
import com.example.product.domain.dto.ProductRequestDTO;
import com.example.product.domain.dto.ProductResponseDTO;
import com.example.product.domain.dto.ProductUpdateDTO;
import com.example.product.domain.entity.ProductEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository; 

    public ProductResponseDTO productCreate(ProductRequestDTO request, String email) {

        System.out.println(">>>> product service productCreate");
        ProductEntity product = request.toEntity(email);
        return ProductResponseDTO.fromEntity(productRepository.save(product));

    }

    public ProductResponseDTO productRead(Long productId) {
        System.out.println(">>>> product service productRead");
        ProductEntity entity = productRepository.findById(productId)
            .orElseThrow(() -> new RuntimeException("상품 없음"));

        return ProductResponseDTO.fromEntity(entity);

    }

    /* 
    Producer(Order) - kafka(topic - update-stock-topic)

    @KafkaListener - String message 수신 - ObjectMapper(JSON->DTO)

    Consumer(Product)
    
    stockConsumer(String message) 메서드 호출이 발생하여 재고 업데이트
    */
    
    @KafkaListener(topics = "update-stock-topic")
    public void stockConsumer(String message){
        ObjectMapper mapper = new ObjectMapper();
        ProductUpdateDTO productUpdateDTO = null ;
        try {
            productUpdateDTO = mapper.readValue(message, ProductUpdateDTO.class);
            ProductEntity productEntity = productRepository
                                            .findById(productUpdateDTO.getProductId())
                                            .orElseThrow(()-> new RuntimeException("상품"));
            productEntity.updateStockQty(productUpdateDTO.getQty());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
