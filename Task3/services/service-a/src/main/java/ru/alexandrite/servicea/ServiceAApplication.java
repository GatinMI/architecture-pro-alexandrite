package ru.alexandrite.servicea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * service-a — «сервис заказов».
 *
 * <p>Аналог Shop/CRM API в стеке «Александрит»: принимает новый заказ
 * и для его оформления вызывает сервис расчёта цены (service-b).
 * Каждый запрос обрабатывается как SERVER span, вызов service-b —
 * как CLIENT span с W3C-пропагацией trace-id через заголовки.
 */
@SpringBootApplication
public class ServiceAApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceAApplication.class, args);
    }
}
