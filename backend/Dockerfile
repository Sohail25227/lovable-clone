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

# Heap ke alawa JVM metaspace, code cache, GC ka apna dhaancha aur har thread ka stack bhi
# leta hai — yeh sab container ki limit mein ginte hain, heap ki limit mein nahi. 75% pe heap
# akela 384 MB tak ja sakta tha, aur idle RSS hi 345 MB thi: pehli asli request pe container
# 512 se ooper chala gaya aur Render ne "Out of memory" ke baad service band kar di.
# 50% pe heap 256 MB, aur baaki sab ke liye 256 MB bachta hai.
#
# MaxMetaspaceSize ki koi default seema nahi hoti. Bina chhat ke badhti classes container ko
# chupchaap maar deti hain; chhat ke saath saaf OutOfMemoryError: Metaspace milti hai.
#
# Stack 2 MB se 512 KB. Yeh app deep recursion nahi karti, aur bachat threads ki ginti se
# guna hoti hai. Threads khud application-prod.yml mein kam ki gayi hain.
#
# Heap khatam ho to process ko mar jana chahiye taaki platform use naya shuru kare. Iske bina
# JVM GC mein ghoomti rehti hai aur service zinda dikhti hai par jawab nahi deti — jo monitoring
# ke liye crash se badtar haalat hai
#
# TieredStopAtLevel=1 JIT ko sirf C1 tak rakhta hai. Free tier pe CPU hi kami hai, aur
# background compilation usi CPU se hoti hai jispe app boot ho rahi hoti hai: 0.25 CPU wale
# container mein startup 163s se 67s pe aa gaya aur memory 332 MB se 288 MB (code cache aur
# compiler threads dono chhote). Startup yahan sirf aaram ki baat nahi — pichhli deploy isliye
# mari thi ki platform ko port time pe mila hi nahi.
#
# Keemat yeh hai ki C2 ka optimised code kabhi nahi banta, to lambe samay ki CPU-heavy
# throughput kam rehti hai. Is app mein woh matter nahi karta: har request ka waqt Groq ke
# jawab ka intezar hai, hamare loops nahi
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:MaxMetaspaceSize=128m -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

# PORT platform inject karta hai; 8081 local ke liye fallback hai
ENV PORT=8081
EXPOSE 8081

# exec form ke andar sh, taaki JAVA_OPTS expand ho aur exec ke baad java PID 1 bane —
# warna shell PID 1 rehta hai aur SIGTERM app tak nahi pahunchta, yaani graceful
# shutdown ke bajaye platform use maar deta hai
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
