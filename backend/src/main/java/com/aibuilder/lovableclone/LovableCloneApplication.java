package com.aibuilder.lovableclone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * App ka entry point — yahi se Spring Boot start hota hai.
 *
 * @SpringBootApplication = 3 cheezein ek saath:
 * 1. @Configuration  → yeh class config hai
 * 2. @EnableAutoConfiguration → Spring Boot auto setup (Tomcat, JSON, etc.)
 * 3. @ComponentScan → isi package aur neeche ke packages mein beans dhoondhta hai
 *
 * UserDetailsServiceAutoConfiguration exclude hai. Kahin UserDetailsService bean na
 * milne par Boot khud ek in-memory user bana deta hai aur uska password startup logs
 * mein chhap deta hai — production logs mein ek credential, chahe woh kaam ka ho ya na ho.
 *
 * Filhaal woh user pahunch se bahar hai: login AuthService karta hai, jo seedha
 * UserRepository se user nikalta hai aur BCrypt se milata hai, aur dono filter chains
 * mein httpBasic/formLogin enable nahi hai — yaani username-password lene wala koi
 * raasta hi nahi. Asli khatra kal ka hai: koi httpBasic() add kare to woh in-memory
 * account us hi pal se asli account ban jayega, jiska password logs mein pada hai.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LovableCloneApplication {

    public static void main(String[] args) {
        SpringApplication.run(LovableCloneApplication.class, args);
    }
}
