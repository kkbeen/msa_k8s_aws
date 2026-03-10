package com.example.apigateway;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter implements GlobalFilter {

    @Value("${jwt.secret}")
    private String secret ; 
    private Key key ; 

    // token 검증없이 통과하는 endpoint 등록
    private static final List<String> WHITE_LIST_PATHS = List.of(
        "/users/signIn",
        "/health/alive",
        "/product/list"
    );

    @PostConstruct
    private void init() {
        System.out.println(">>>> JwtAuthenticationFilter init jwt secret : "+secret);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        System.out.println(">>>> JwtAuthenticationFilter init token validation : ");
        String bearerToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        System.out.println(">>>> JWTAuthenticationFilter filter bearerToken: "+bearerToken);

        String endPoint = exchange.getRequest().getURI().getRawPath() ;
        System.out.println(">>>> JwtAuthenticationFilter User EndPoint : "+endPoint);
        String method = exchange.getRequest().getMethod().name() ; 
        System.out.println(">>>> JwtAuthenticationFilter Request Method : "+method);

        if(WHITE_LIST_PATHS.contains(endPoint)) {
            System.out.println(">>>> JwtAuthenticationFilter filter WHITE_LIST_PATHS : ");
            return chain.filter(exchange);
        }

        try {

            System.out.println(">>>> JwtAuthenticationFilter Authorization : "+bearerToken);
            if( bearerToken == null || !bearerToken.startsWith("Bearer")){
                System.out.println(">>>> JwtAuthenticationFilter Not Authorization : ");
                throw new RuntimeException("JwtAuthenticationFilter token exception");
            }
            String token = bearerToken.substring(7);
            System.out.println(">>>> JwtAuthenticationFilter token : "+token);
            
            Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();

            String email = claims.getSubject();
            System.out.println(">>>> JwtAuthenticationFilter claims get email : "+email);

            // JwtProvider 의해서 Role 입력된 경우에만 해당
            String role = claims.get( "role", String.class);
            System.out.println(">>>> JwtAuthenticationFilter claims get role : "+role);

            // X-User-Id 변수로 email 값과 Role 추가
            // X custion header 라는 것을 의미하는 관례....
            ServerWebExchange modifyExchange = exchange.mutate()
                .request(builder -> builder
                            .header("X-User-Email", email)
                            .header("X-User-Role", "Role_"+role)
                        ).build();

            return chain.filter(modifyExchange);

        } catch(Exception e) {
            e.printStackTrace();
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
    
}
