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

# **The app runs at:**
  👉 http://localhost:9191


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

                              



