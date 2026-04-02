package com.example.app;

public class DemoApplication {
    public static void main(String[] args) {
        GreetingService service = new GreetingService();
        String result = service.sayHello("EaseAgent");
        System.out.println("[app] " + result);
    }
}

