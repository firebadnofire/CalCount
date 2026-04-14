# Calorie Counter App
## Product Scope Draft (Version 1)

---

## 1. Overview

This app is a simple calorie counter with a Material 3 UI. Its first release focuses on fast manual food entry, reliable serving math, and clear daily tracking.

The core interaction:
- User creates a food using nutrition-label-style fields
- Defines serving size and serving weight
- Logs consumption either by servings or by weight
- App calculates calories and macros automatically

---

## 2. Product Goals

- Fast, low-friction food logging
- Familiar nutrition-label fields
- Support both serving-based and weight-based input
- Clear daily calorie tracking
- Reusable food entries

---

## 3. Non-Goals (v1)

- Barcode scanning
- Online food database
- Photo recognition
- Recipes / meal planning
- Social features
- Cloud sync

---

## 4. Core Principle

All nutrition values are stored **per serving**.

Serving weight is used to convert between:
- servings ↔ weight

User chooses ONE input mode:
- Servings (e.g. 1.5 servings)
- Weight (e.g. 75g)

---

## 5. Data Model

### Food
- id
- name
- servingDescription
- servingWeightGrams
- calories
- fat
- carbs
- protein
- optional nutrients

### LogEntry
- id
- foodId
- mealType
- inputMode
- consumedServings
- consumedWeightGrams
- calculatedCalories
- calculatedMacros

### Goals
- dailyCalories
- macroTargets
- preferredUnit

---

## 6. Required Food Fields

- Food name
- Serving description (e.g. "1 bar")
- Serving weight (grams)
- Calories
- Fat
- Carbohydrates
- Protein

## Optional Fields

- Saturated fat
- Fiber
- Sugars
- Sodium
- Servings per container

---

## 7. Calculation Logic

### Servings Mode
calories = perServingCalories × servings

### Weight Mode
servingFraction = consumedWeight / servingWeight
calories = perServingCalories × servingFraction

Example:
- Serving = 55g
- Calories = 220
- Consumed = 27.5g
→ 0.5 servings → 110 calories

---

## 8. Screens

### Today
- Calories consumed / remaining
- Macro summary
- Meal sections

### Food Library
- Search foods
- Recent foods
- Edit / reuse

### Create Food
- Nutrition-label-style form

### Log Food
- Toggle: Servings vs Weight
- Input amount
- Live calculation preview

### Settings
- Calorie goal
- Macro targets
- Unit preferences

---

## 9. UX Rules

- Do not ask for both servings AND weight at the same time
- Show calculated values before saving
- Keep required fields minimal
- Prioritize reuse (recent foods)
- Avoid overcomplicated charts

---

## 10. Edge Cases

- Serving weight cannot be zero
- Allow decimal servings
- Support small weight values
- Reject negative nutrition values
- Convert ounces → grams internally

---

## 11. Acceptance Criteria

- User can create a food quickly
- Logging works in both modes
- Math is correct and consistent
- Dashboard updates instantly
- Food reuse is easy

---

## 12. Build Priority

### P0
- Data model
- Calculation logic
- Create + Log screens

### P1
- Dashboard
- Food search + recents

### P2
- Optional nutrients
- UI polish

---

## 13. Future Features

- Barcode scanning
- Smart suggestions
- Recipes
- Cloud sync
- Integrations

---

## 14. Bottom Line

A clean manual calorie tracker with strong serving math and a simple Material 3 interface.

Not a bloated nutrition platform.

