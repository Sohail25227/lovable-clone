# syntax=docker/dockerfile:1
# Ooper wali line ek parser directive hai, comment nahi, aur usse pehle kuch nahi aa sakta:
# ek comment ya khaali line ke baad Docker directives dhoondhna band kar deta hai aur woh
# chupchaap bekaar ho jati hai. Zarurat neeche `RUN --mount` ki hai, jo frontend ka feature
# hai — purane builder pe uske bina parse error milta hai.

# Do stage: build ke liye poora JDK aur Maven chahiye, chalane ke liye sirf JRE.
# Ek hi stage rakhne se image mein Maven, source aur ~/.m2 ka poora cache aa jata —
# kai sau MB, aur build tools production mein bethe rehte
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

# .mvn/settings.xml jaan-boojh ke copy nahi hoti: woh corporate mirror bypass karne ke
# liye hai, aur container mein aisa koi mirror hi nahi. Yahan Maven seedha Central pe jata hai.
#
# Cache mount se ~/.m2 build ke beech bacha rehta hai, par image mein nahi jata.
# Pehle yahan `dependency:go-offline` ek alag layer mein tha; woh hata diya, kyunki woh
# har profile aur har plugin ki dependencies resolve karta hai — package se kai guna zyada
# download, aur uske baad package phir bhi apna download karta hai.
#
# Tests chalte hain, skip nahi hote: image sirf tab banti hai jab suite green ho.
# Inhe kisi secret ki zarurat nahi (validator pure function hai, retry test sirf
# properties binding dekhta hai), isliye build ke liye env chahiye hi nahi
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package

# Alpine variant chhota hai par sirf amd64 pe milta hai, aur is machine (Apple Silicon)
# pe build "no match for platform in manifest" se ruk jata hai. Yeh tag multi-arch hai,
# to image locally bhi banti hai aur platform ke amd64 builder pe bhi
FROM eclipse-temurin:17-jre
WORKDIR /app

# Root pe chalne ki koi wajah nahi. Container escape ho to attacker root na ho.
# Yeh Debian hai, Alpine nahi — BusyBox ka `adduser -S` yahan nahi chalta
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build --chown=app:app /build/target/lovable-clone-*.jar app.jar

USER app

# JVM container ki memory dekh leta hai, par default heap uska ek chauthai leta hai.
# 512 MB wale free tier pe woh 128 MB heap hai — Boot + JPA + Flyway + Spring AI usme
# boot nahi hote. 75% is app ko jagah deta hai aur non-heap ke liye bhi bacha rehta hai
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# PORT platform inject karta hai; 8081 local ke liye fallback hai
ENV PORT=8081
EXPOSE 8081

# exec form ke andar sh, taaki JAVA_OPTS expand ho aur exec ke baad java PID 1 bane —
# warna shell PID 1 rehta hai aur SIGTERM app tak nahi pahunchta, yaani graceful
# shutdown ke bajaye platform use maar deta hai
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
