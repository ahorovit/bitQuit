/*********************************************************************
 This is an example for our nRF51822 based Bluefruit LE modules

 Pick one up today in the adafruit shop!

 Adafruit invests time and resources providing this open source code,
 please support Adafruit and open-source hardware by purchasing
 products from Adafruit!

 MIT license, check LICENSE for more information
 All text above, and the splash screen below must be included in
 any redistribution
*********************************************************************/

#include <Arduino.h>
#include <SPI.h>
#include <Servo.h> 

#if not defined (_VARIANT_ARDUINO_DUE_X_) && not defined (_VARIANT_ARDUINO_ZERO_)
  #include <SoftwareSerial.h>
#endif

#include "Adafruit_BLE.h"
#include "Adafruit_BluefruitLE_SPI.h"
#include "Adafruit_BluefruitLE_UART.h"

#include "BluefruitConfig.h"

/*=========================================================================
    APPLICATION SETTINGS

    FACTORYRESET_ENABLE       Perform a factory reset when running this sketch
   
                              Enabling this will put your Bluefruit LE module
                              in a 'known good' state and clear any config
                              data set in previous sketches or projects, so
                              running this at least once is a good idea.
   
                              When deploying your project, however, you will
                              want to disable factory reset by setting this
                              value to 0.  If you are making changes to your
                              Bluefruit LE device via AT commands, and those
                              changes aren't persisting across resets, this
                              is the reason why.  Factory reset will erase
                              the non-volatile memory where config data is
                              stored, setting it back to factory default
                              values.
       
                              Some sketches that require you to bond to a
                              central device (HID mouse, keyboard, etc.)
                              won't work at all with this feature enabled
                              since the factory reset will clear all of the
                              bonding data stored on the chip, meaning the
                              central device won't be able to reconnect.
    MINIMUM_FIRMWARE_VERSION  Minimum firmware version to have some new features
    MODE_LED_BEHAVIOUR        LED activity, valid options are
                              "DISABLE" or "MODE" or "BLEUART" or
                              "HWUART"  or "SPI"  or "MANUAL"
    -----------------------------------------------------------------------*/
    #define FACTORYRESET_ENABLE         1
    #define MINIMUM_FIRMWARE_VERSION    "0.6.6"
    #define MODE_LED_BEHAVIOUR          "MODE"
/*=========================================================================*/

// Create the bluefruit object, either software serial...uncomment these lines
/*
SoftwareSerial bluefruitSS = SoftwareSerial(BLUEFRUIT_SWUART_TXD_PIN, BLUEFRUIT_SWUART_RXD_PIN);

Adafruit_BluefruitLE_UART ble(bluefruitSS, BLUEFRUIT_UART_MODE_PIN,
                      BLUEFRUIT_UART_CTS_PIN, BLUEFRUIT_UART_RTS_PIN);
*/

/* ...or hardware serial, which does not need the RTS/CTS pins. Uncomment this line */
// Adafruit_BluefruitLE_UART ble(Serial1, BLUEFRUIT_UART_MODE_PIN);

/* ...hardware SPI, using SCK/MOSI/MISO hardware SPI pins and then user selected CS/IRQ/RST */
Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_CS, BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);

/* ...software SPI, using SCK/MOSI/MISO user-defined SPI pins and then user selected CS/IRQ/RST */
//Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_SCK, BLUEFRUIT_SPI_MISO,
//                             BLUEFRUIT_SPI_MOSI, BLUEFRUIT_SPI_CS,
//                             BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);


// A small helper
void error(const __FlashStringHelper*err) {
//  Serial.println(err);
  while (1);
}

/**************************************************************************/
/*!
    @brief  Sets up the HW an the BLE module (this function is called
            automatically on startup)
*/
/**************************************************************************/

Servo myservo;  // create servo object to control a servo 
                // twelve servo objects can be created on most boards
 
int pos = 0;    // variable to store the servo position 

// setting pins. THESE ARE SOLDERED, DO NOT CHANGE!!
const int lidSwitchHigh = 3;
const int lidSwitchRead = 2;
const int servoControlPin = 9;
const int dividerPinHigh = 13;
const int cigPin = 4;






void setup(void)
{
  
  ble.begin(VERBOSE_MODE);
  // Disable command echo from Bluefruit 
  ble.echo(false);

//  Serial.println("Requesting Bluefruit info:");
  // Print Bluefruit information 
//  ble.info();

  ble.verbose(false);  // debug info is a little annoying after this point!

  // Wait for connection 
  while (! ble.isConnected()) {
      Serial.println("BLE not connected");
      delay(500);
  }

  // LED Activity command is only supported from 0.6.6
  if ( ble.isVersionAtLeast(MINIMUM_FIRMWARE_VERSION) )
  {
    // Change Mode LED Activity
//    Serial.println(F("******************************"));
//    Serial.println(F("Change LED activity to " MODE_LED_BEHAVIOUR));
    ble.sendCommandCheckOK("AT+HWModeLED=" MODE_LED_BEHAVIOUR);
//    Serial.println(F("******************************"));
  }



  //BitQuit

  
  pinMode(lidSwitchHigh, OUTPUT);
  digitalWrite(lidSwitchHigh, HIGH); // provides 3.3V for reed switch
  pinMode(dividerPinHigh, OUTPUT);
  digitalWrite(dividerPinHigh, HIGH); //provides 3.3V to voltage divider circuit
  pinMode(cigPin, INPUT); // reads divider voltage range 0-1023
  pinMode(lidSwitchRead, INPUT); // reads a value of 1 when magnet is close to reed switch (lid closed)

}

/**************************************************************************/
/*!
    @brief  Constantly poll for new command or response data
*/
/**************************************************************************/
void loop(void)
{

  String output;
  int cigCount, pinLvl, isClosed = 0, lockDur = 5;
  bool wasOpened = false;
  char inputs[BUFSIZE+1];

  // must wait for box to open before reinitiating cycle
  while (!wasOpened) {
    if (!digitalRead(lidSwitchRead))
      wasOpened = true;

//    Serial.println("Still Closed...");
    delay(500);
  }

  // If lid isn't closed, do nothing
  while (!isClosed) {
    isClosed = digitalRead(lidSwitchRead);
    pinLvl = analogRead(cigPin);    
    delay(500);
//    Serial.println("Still Open...");

  }

  // Convert analogRead level to cigarette count
  if (pinLvl >= 0 && pinLvl < 560)
    cigCount = 10; 
  else if (pinLvl >= 560 && pinLvl < 595)
    cigCount = 9;
  else if (pinLvl >= 595 && pinLvl < 625)
    cigCount = 8;
  else if (pinLvl >= 625 && pinLvl < 655)
    cigCount = 7;
  else if (pinLvl >= 655 && pinLvl < 690)
    cigCount = 6;
  else if (pinLvl >= 690 && pinLvl < 725)
    cigCount = 5;
  else if (pinLvl >= 725 && pinLvl < 775)
    cigCount = 4;
  else if (pinLvl >= 775 && pinLvl < 825)
    cigCount = 3;
  else if (pinLvl >= 825 && pinLvl < 900)
    cigCount = 2;
  else if (pinLvl >= 900 && pinLvl < 990)
    cigCount = 1;
  else if (pinLvl >= 990 && pinLvl <= 1023)
    cigCount = 0;    
  else
    cigCount = -1;
  String num = String(cigCount); // Must create string
  output = "c " + num;
   


  myservo.attach(servoControlPin);
  for(pos = 100; pos <= 145; pos += 1) // closes latch
  {                                  // in steps of 1 degree 
    myservo.write(pos);              // tell servo to go to position in variable 'pos' 
    delay(10);                       // waits 15ms for the servo to reach the position 
  } 
  myservo.detach();  //"turns off" servo. MUST USE WHEN SERVO IS TO REMAIN STILL

  // Print State  
  Serial.println(pinLvl);

  ble.print("AT+BLEUARTTX=");
  ble.println(output);
  if (! ble.waitForOK() ) {
    Serial.println(F("Failed to send?"));
  }

  delay(100);
  
  // Locked duration
  for (; lockDur > 0; lockDur--)
  {
    ble.print("AT+BLEUARTTX=");
    ble.println(lockDur);
    if (! ble.waitForOK() ) {
      Serial.println(F("Failed to send?"));
    }
    
    delay(1000);
  }
  
  myservo.attach(servoControlPin);
  for(pos = 145; pos>=100; pos-=1)     // opens latch
  {                                
    myservo.write(pos);              // tell servo to go to position in variable 'pos' 
    delay(10);                       // waits 15ms for the servo to reach the position 
  } 
  myservo.detach();  //"turns off" servo. MUST USE WHEN SERVO IS TO REMAIN STILL


  String open = "o"; // Must create string

  ble.print("AT+BLEUARTTX=");
  ble.println(open);
  if (! ble.waitForOK() ) {
    Serial.println(F("Failed to send?"));
  }


  // Check for user input
/*
  if ( getUserInput(inputs, BUFSIZE) )
  {
    // Send characters to Bluefruit
    Serial.print("[Send] ");
    Serial.println(inputs);

    ble.print("AT+BLEUARTTX=");
    ble.println(inputs);

    // check response stastus
    if (! ble.waitForOK() ) {
      Serial.println(F("Failed to send?"));
    }
  }

  // Check for incoming characters from Bluefruit
  ble.println("AT+BLEUARTRX");
  ble.readline();
  if (strcmp(ble.buffer, "OK") == 0) {
    // no data
    return;
  }
  // Some data was found, its in the buffer
  Serial.print(F("[Recv] ")); Serial.println(ble.buffer);
  ble.waitForOK();
*/

}




/**************************************************************************/
/*!
    @brief  Checks for user input (via the Serial Monitor)
*/
/**************************************************************************/
/*
bool getUserInput(char buffer[], uint8_t maxSize)
{
  // timeout in 100 milliseconds
  TimeoutTimer timeout(100);

  memset(buffer, 0, maxSize);
  while( (!Serial.available()) && !timeout.expired() ) { delay(1); }

  if ( timeout.expired() ) return false;

  delay(2);
  uint8_t count=0;
  do
  {
    count += Serial.readBytes(buffer+count, maxSize);
    delay(2);
  } while( (count < maxSize) && (Serial.available()) );

  return true;
}
*/
