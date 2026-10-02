package ru.alexandrite.serviceb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * service-b — «сервис расчёта цены».
 *
 * <p>Аналог модуля расчёта стоимости в MES: по id заказа «считает»
 * цену (с небольшой задержкой, имитируя обработку 3D-модели) и
 * возвращает результат. Принимает входящий trace context из
 * заголовков traceparent/tracestate и присоединяет свой span
 * к тому же трейсу.
 */
@SpringBootApplication
public class ServiceBApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceBApplication.class, args);
    }
}
