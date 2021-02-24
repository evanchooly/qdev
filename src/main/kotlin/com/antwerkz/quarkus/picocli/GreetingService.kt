package com.antwerkz.quarkus.picocli

import javax.enterprise.context.Dependent

@Dependent
class GreetingService {
    fun sayHello(name: String?) {
        System.out.printf("Hello dear %s!\n", name)
    }
}
