#include <ESP8266WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <Servo.h>

const char* ssid = "Berk";
const char* password = "1923ANK1453ART08";

const char* mqttServer = "36288b3a8cbb4d6f85c7ab5b43dc30db.s1.eu.hivemq.cloud";
const int mqttPort = 8883;
const char* mqttUser = "hberks";
const char* mqttPassword = "Berk0808";
const char* mqttClientId = "ESP8266_Robot_Control_MQTT";
const char* mqttTopic = "robot/control";
const char* mqttAlarmTopic = "robot/alarm";

const byte ENA = D6;
const byte IN1 = D2;
const byte IN2 = D3;
const byte IN3 = D4;
const byte IN4 = D5;
const byte ENB = D7;
const byte SERV = D8;
const byte SU_MOTOR_PIN = D1;
const byte yangin_sensoru = D0;
const byte yanginled = 1; 

const int hiz = 150;
const int hizs = 150;
const int turboHiz = 255;
volatile int currentHiz = hiz;
volatile char karakter = 'S';

const int SERVO_CENTER_PHYSICAL = 50;

WiFiClientSecure wifiClientSecure;
PubSubClient mqttClient(wifiClientSecure);
Servo servoMotor;

bool currentFireState = false;
unsigned long lastFirePublishTime = 0;
const unsigned long firePublishInterval = 3000;

void setupPins();
void setupWiFiSTA();
void setupMQTT();
void reconnectMQTT();
void mqttCallback(char* topic, byte* payload, unsigned int length);
void stopMotors();
void executeCommand();
void checkFireSensorAndPublish();

void setup() {
    Serial.begin(9600);
    delay(100);
    Serial.println("\nESP8266 MQTT Robot Baslatiliyor...");

    setupPins();
    setupWiFiSTA();
    setupMQTT();

    Serial.println("Kurulum Tamamlandi. MQTT Komutlari bekleniyor.");
}

void loop() {
    if (!mqttClient.connected()) {
        reconnectMQTT();
    }
    mqttClient.loop();

    checkFireSensorAndPublish();
    executeCommand();
    delay(10);
}

void checkFireSensorAndPublish() {
    int ates = digitalRead(yangin_sensoru);
    bool newFireState = (ates == LOW);

    if (newFireState != currentFireState || (millis() - lastFirePublishTime > firePublishInterval)) {
        if (newFireState) {
            Serial.println("Yangin algilandi! MQTT'ye 'FIRE' gonderiliyor.");
            mqttClient.publish(mqttAlarmTopic, "FIRE", true);
            digitalWrite(yanginled, HIGH);
        } else {
            Serial.println("Yangin durumu normal. MQTT'ye 'NO_FIRE' gonderiliyor.");
            mqttClient.publish(mqttAlarmTopic, "NO_FIRE", true);
            digitalWrite(yanginled, LOW);
        }
        currentFireState = newFireState;
        lastFirePublishTime = millis();
    }
}

void setupPins() {
    pinMode(ENA, OUTPUT);
    pinMode(IN1, OUTPUT);
    pinMode(IN2, OUTPUT);
    pinMode(IN3, OUTPUT);
    pinMode(IN4, OUTPUT);
    pinMode(ENB, OUTPUT);
    pinMode(SU_MOTOR_PIN, OUTPUT);
    digitalWrite(SU_MOTOR_PIN, LOW);
    pinMode(yangin_sensoru, INPUT);
    pinMode(yanginled, OUTPUT);
    digitalWrite(yanginled, LOW);

    servoMotor.attach(SERV);
    servoMotor.write(SERVO_CENTER_PHYSICAL);
    stopMotors();
    currentHiz = hiz;
    Serial.println("Pinler ayarlandi.");
}

void setupWiFiSTA() {
    Serial.printf("WiFi agina baglaniliyor: %s\n", ssid);
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, password);

    Serial.print("Baglaniliyor");
    unsigned long startTime = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - startTime < 30000) {
        delay(500);
        Serial.print(".");
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.println("\nWiFi Baglantisi Basarili!");
        Serial.print("IP Adresi: ");
        Serial.println(WiFi.localIP());
    } else {
        Serial.println("\nWiFi Baglantisi Basarisiz!");
        Serial.println("ESP8266 yeniden baslatiliyor...");
        delay(3000);
        ESP.restart();
    }
}

void setupMQTT() {
    Serial.println("MQTT Ayarlaniyor...");
    wifiClientSecure.setInsecure();
    mqttClient.setServer(mqttServer, mqttPort);
    mqttClient.setCallback(mqttCallback);
    Serial.println("MQTT Ayarlandi.");
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
    Serial.print("Mesaj alindi [");
    Serial.print(topic);
    Serial.print("] Tam Mesaj: '");
    char cmdBuffer[length + 1];
    for (unsigned int i = 0; i < length; i++) {
        Serial.print((char)payload[i]);
        cmdBuffer[i] = (char)payload[i];
    }
    cmdBuffer[length] = '\0';
    Serial.println("'");

    if (length > 0) {
        char firstChar = cmdBuffer[0];

        if (firstChar == 'X' && length > 1) {
            String angleStr = "";
            for (unsigned int i = 1; i < length; i++) {
                if (isdigit(cmdBuffer[i])) {
                    angleStr += cmdBuffer[i];
                } else {
                    break;
                }
            }
            if (angleStr.length() > 0) {
                int html_slider_value = angleStr.toInt();
                html_slider_value = constrain(html_slider_value, 0, 180);
                int logical_degrees = html_slider_value - 90;
                int servo_write_angle = SERVO_CENTER_PHYSICAL + logical_degrees;
                servo_write_angle = constrain(servo_write_angle, 0, 180);
                servoMotor.write(servo_write_angle);
                Serial.print("-> Servo: HTML="); Serial.print(html_slider_value);
                Serial.print(", Mantiksal="); Serial.print(logical_degrees);
                Serial.print(", Servo="); Serial.println(servo_write_angle);
            } else {
                 Serial.println("-> Servo Acisi degeri parse edilemedi.");
            }
        } else if (firstChar == 'T') {
            if (currentHiz != turboHiz) {
                currentHiz = turboHiz;
                Serial.println("-> Turbo Aktif Edildi!");
            }
        } else if (firstChar == 'N') {
            if (currentHiz != hiz) {
                currentHiz = hiz;
                Serial.println("-> Turbo Kapatildi!");
            }
        } else if (firstChar == 'W') {
            digitalWrite(SU_MOTOR_PIN, HIGH);
            Serial.println("-> Su Motoru ACILDI");
        } else if (firstChar == 'w') {
            digitalWrite(SU_MOTOR_PIN, LOW);
            Serial.println("-> Su Motoru KAPATILDI");
        } else if (String("dcabS").indexOf(firstChar) != -1) {
            if (karakter != firstChar) {
                karakter = firstChar;
                Serial.print("-> Hareket Komutu: "); Serial.println(karakter);
            }
        } else {
            Serial.print("-> Bilinmeyen Komut Alindi: "); Serial.println(cmdBuffer);
        }
    } else {
        Serial.println("-> Bos MQTT Mesaji Alindi.");
    }
}

void reconnectMQTT() {
    while (!mqttClient.connected()) {
        Serial.print("MQTT Baglantisi deneniyor...");
        if (mqttClient.connect(mqttClientId, mqttUser, mqttPassword)) {
            Serial.println(" Baglandi");
            if (mqttClient.subscribe(mqttTopic)) {
                 Serial.print("Konuya abone olundu: ");
                 Serial.println(mqttTopic);
                 checkFireSensorAndPublish();
            } else {
                 Serial.println("Konuya abone olma basarisiz!");
                 mqttClient.disconnect();
                 delay(1000);
            }
        } else {
            Serial.print(" Basarisiz, rc=");
            Serial.print(mqttClient.state());
            Serial.println(" 5 saniye icinde tekrar denenecek");
            delay(5000);
        }
    }
}

void stopMotors() {
    digitalWrite(IN1, LOW); digitalWrite(IN2, LOW);
    digitalWrite(IN3, LOW); digitalWrite(IN4, LOW);
    analogWrite(ENA, 0); analogWrite(ENB, 0);

    
}

void executeCommand() {
    static char lastLoggedKarakter = ' ';
    static int lastLoggedSpeed = -1;
    static int lastLoggedTurnSpeed = -1;

    bool stateChangedForLogging = false;

    if (karakter != lastLoggedKarakter) {
        stateChangedForLogging = true;
    } else {
        if ((karakter == 'd' || karakter == 'c') && currentHiz != lastLoggedSpeed) {
            stateChangedForLogging = true;
        } else if ((karakter == 'a' || karakter == 'b') && hizs != lastLoggedTurnSpeed) {
            stateChangedForLogging = true;
        }
    }

    switch (karakter) {
        case 'd':
            if (stateChangedForLogging) {
                Serial.print("EXE: Ileri, Hiz: "); Serial.println(currentHiz);
            }
            digitalWrite(IN1, HIGH); digitalWrite(IN2, LOW);
            digitalWrite(IN3, LOW); digitalWrite(IN4, HIGH);
            analogWrite(ENA, currentHiz); analogWrite(ENB, currentHiz);
            lastLoggedSpeed = currentHiz;
            break;
        case 'c':
            if (stateChangedForLogging) {
                Serial.print("EXE: Geri, Hiz: "); Serial.println(currentHiz);
            }
            digitalWrite(IN1, LOW); digitalWrite(IN2, HIGH);
            digitalWrite(IN3, HIGH); digitalWrite(IN4, LOW);
            analogWrite(ENA, currentHiz); analogWrite(ENB, currentHiz);
            lastLoggedSpeed = currentHiz;
            break;
        case 'a':
            if (stateChangedForLogging) {
                Serial.print("EXE: Sol, Hiz: "); Serial.println(hizs);
            }
            digitalWrite(IN1, LOW); digitalWrite(IN2, HIGH);
            digitalWrite(IN3, LOW); digitalWrite(IN4, HIGH);
            analogWrite(ENA, hizs); analogWrite(ENB, hizs);
            lastLoggedTurnSpeed = hizs;
            break;
        case 'b':
            if (stateChangedForLogging) {
                Serial.print("EXE: Sag, Hiz: "); Serial.println(hizs);
            }
            digitalWrite(IN1, HIGH); digitalWrite(IN2, LOW);
            digitalWrite(IN3, HIGH); digitalWrite(IN4, LOW);
            analogWrite(ENA, hizs); analogWrite(ENB, hizs);
            lastLoggedTurnSpeed = hizs;
            break;
        case 'S':
            if (stateChangedForLogging || lastLoggedKarakter != 'S') {
                Serial.println("EXE: Dur");
            }
            stopMotors();
            lastLoggedSpeed = 0;
            lastLoggedTurnSpeed = 0;
            break;
        default:
             if (stateChangedForLogging) {
                Serial.print("EXE: Bilinmeyen ("); Serial.print(karakter); Serial.println(") - Durduruluyor.");
             }
            stopMotors();
            karakter = 'S';
            lastLoggedSpeed = 0;
            lastLoggedTurnSpeed = 0;
            break;
    }

    if (stateChangedForLogging) {
        lastLoggedKarakter = karakter;
    }
}