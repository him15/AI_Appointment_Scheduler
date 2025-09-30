# 🩺 AI-Powered Appointment Scheduler Assistant

This project is a **Spring Boot backend service** that parses **natural language or document-based appointment requests** and converts them into structured scheduling data.

It supports:
- **Typed text input** (e.g., "Book dentist next Friday at 3pm")
- **Image input** (scanned notes, emails, photos) using OCR (Tesseract)

The pipeline: **OCR → Text Preprocessing → Entity Extraction → Normalization → Guardrails → JSON Output**

---

## 🚀 Tech Stack

- **Java 21**, **Spring Boot**
- **Maven** (build tool)
- **Tesseract OCR (Tess4J)** — extract text from images
- **Natty** — natural language date/time parser
- **Custom NLP** — fuzzy matching (Levenshtein) for department detection
- **REST API** — JSON endpoints
- **Ngrok / Cloudflare Tunnel** — expose local server for testing


## ⚙️ Setup Instructions

- **Clone the repository**
- **Prerequisites**
   • Java 17+
   • Maven 3.6+
   • Tesseract OCR installed locally
          •	macOS: brew install tesseract
          •	Ubuntu/Debian: sudo apt-get install tesseract-ocr -y
  
- **Install dependencies & build**
  • mvn clean install
  
- **Run the Spring Boot app**
  • mvn spring-boot:run

### **The app runs at:**
  👉 http://localhost:9191/api/parse/image/   
  👉 http://localhost:9191/api/parse/text/

---

# **Demo**


### check with some correct output.

<img width="877" height="757" alt="2" src="https://github.com/user-attachments/assets/bf6a10d3-6722-4fd5-9824-9f907ae361e8" />


### check for some wrong text 

<img width="922" height="664" alt="1" src="https://github.com/user-attachments/assets/a7b34daa-19c2-40d0-b1bd-800df6937884" />


### check with image some long email 

<img width="1036" height="769" alt="3" src="https://github.com/user-attachments/assets/ce61d8fb-60cb-46de-b9e7-b5d6d92e7445" />



---


## 🏗️ Architecture

```text
            ┌───────────────┐
            │   Client       │
            │ (Text / Image) │
            └───────┬───────┘
                    │
                    ▼
          ┌───────────────────┐
          │  REST Controller  │
          │ (ParseController) │
          └───────┬──────────┘
                  │
                  ▼
        ┌─────────────────────┐
        │   PipelineService   │
        └───────┬────────────┘
                │
 ┌──────────────┼───────────────────────────────────┐
 │              │                                   │
 ▼              ▼                                   ▼
TextPreprocessor   EntityExtractor (Fuzzy dept,   OCR Service
(lowercase,        date/time phrase)              (Tesseract + preprocessing)
normalize spaces)                                 
 │              │                                   │
 └───────┬──────┘                                   │
         ▼                                          │
    NattyNormalizer (ISO date/time)                 │
         │                                          │
         ▼                                          │
 SimpleConfidenceScorer (assign confidences)        │
         │                                          │
         ▼                                          │
 DefaultGuardrailService (decide status, message, final JSON)
         │
         ▼
  Structured JSON Response

                              



