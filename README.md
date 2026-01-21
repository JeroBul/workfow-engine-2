# Quarkus JSON Workflow Engine (REST API)

Ce projet est un moteur minimal de workflow dynamique Quarkus 🎯 :
- **Les workflows** sont configurés en JSON (import/export).
- **Les étapes (actions)** sont exécutées par mapping sur des beans Java CDI ou gérées dynamiquement via une convention.
- **L’exécution** s’effectue au travers d’une API REST, état/context persisté.
- **Transitions** et conditions : Expressives (AND/OR, MVEL, params typés…).

## Logique AND/OR dans les conditions de transition

Le moteur de workflow permet d’exprimer des règles de transition avancées grâce à une structure flexible avec des **groupes de conditions**.

### Schéma des transitions

Chaque transition dans le workflow (objet `TransitionDTO`) contient une propriété :

- `conditionGroups` : **Liste de groupes** de conditions

Chacun de ces groupes (`ConditionGroupDTO`) contient :

- `mvelConditions` : **Liste d’expressions MVEL** (une String par condition)

---

### Règle d’évaluation : **OR global entre groupes, AND local dans un groupe**

- **Pour qu'une transition soit possible** :  
  **Il suffit qu’UN seul des groupes soit totalement validé.**
- **Pour qu’un groupe soit validé** :  
  **TOUTES les expressions MVEL du groupe doivent être VRAIES en même temps.**

Autrement dit :

- **“conditionGroups”** est une liste de ConditionGroupDTO reliés par un **OU logique** `(OR)`
- **“mvelConditions”** (dans un ConditionGroupDTO) sont reliées entre elles par un **ET logique** `(AND)`

#### Visuellement :

```
Transition possible SI
  ( (condition1_1 AND condition1_2 AND ...)  // Groupe 1
  OR
    (condition2_1 AND condition2_2 AND ...)  // Groupe 2
  OR
    ...
  )
```

#### Exemple :

Pour cette transition :
```json
"conditionGroups": [
    { "mvelConditions": [ "score >= 50", "valid == true" ] },
    { "mvelConditions": [ "isAdmin == true" ] }
]
```

- Le premier groupe : **score >= 50 AND valid == true**
- Le second groupe : **isAdmin == true**

**La transition sera possible si :**
- (_score_ est supérieur ou égal à 50 **ET** _valid_ est vrai)  
  **OU**
- (_isAdmin_ est vrai)

---

### Résumé

- **AND** entre chaque condition au sein du même groupe
- **OR** entre les groupes dans la même transition

Cela permet d’exprimer des logiques complexes comme :
- “La transition est possible pour un admin, ou pour un utilisateur vérifié avec un bon score.”

---

---

## Lancer le projet

```sh
./mvnw compile quarkus:dev
# (ou mvn compile quarkus:dev si Maven global)
```
API par défaut accessible sur [http://localhost:8080/](http://localhost:8080/)

---

## Points clefs de l’API

### 1. **Importer un workflow** (configuration)

```http
POST /admin/workflows/import/{name}
Content-Type: application/json

(body: voir exemple plus bas)
```

### 2. **Créer une instance d’exécution pour un utilisateur**

```http
POST /workflows/instance?workflowName={nom}&username={login}&startActionId={id}
```
- Réponse : instance incluant l’ID, à réutiliser pour les étapes du workflow

### 3. **Connaitre les prochaines actions possibles et l’état contextuel courant**

```http
GET /workflows/instance/{instanceId}/available
```
- Réponse :  
    ```json
    {
      "nextActions": [ { "actionId": "...", ... }, ... ],
      "context": { ... }
    }
    ```

### 4. **Déclencher une action humaine avec paramètres utilisateur**

```http
POST /workflows/instance/{instanceId}/action/{actionId}
Content-Type: application/json

(body: paramètres de l’action, exemple : { "email": "foo@bar.com" })
```
- Exécute l’action humaine
- Enchaine automatiquement toutes les actions AUTOMATIC derrière
- Retourne les prochaines actions humaines disponibles et le contexte

---

## Exemple de workflow JSON

Utilisez ce JSON pour définir un workflow adaptable (ex : “onboard” ci-dessous):

```json
{
  "workflowId": "onboard",
  "actions": [
    { "actionId": "start", "description": "Remplir informations", "type": "HUMAN" },
    { "actionId": "updateDemand", "description": "Remplir informations", "type": "HUMAN",
      "parameters": [
        { "name": "demand", "type": "jsonNode", "required": true }
      ]
    },
    { "actionId": "fillUserInfo", "description": "Remplir informations", "type": "HUMAN",
      "parameters": [
        { "name": "email", "type": "string", "required": true,
          "pattern": "^\\S+@\\S+$", "hint": "Votre email"
        }
      ]
    },
    { "actionId": "validateEmail", "description": "Validation email", "type": "AUTOMATIC" },
    { "actionId": "approveManager", "description": "Validation manager", "type": "HUMAN" }
  ],
  "transitions": [
    { "fromActionId": "start", "toActionId": "fillUserInfo" },
    { "fromActionId": "start", "toActionId": "updateDemand" },
    { "fromActionId": "fillUserInfo", "toActionId": "validateEmail", "conditionGroups": [
      { "mvelConditions": ["email != null"] }
    ]},
    { "fromActionId": "validateEmail", "toActionId": "approveManager", "conditionGroups": [
      { "mvelConditions": ["emailVerified == true"] }
    ]}
  ]
}
```

---

## Scénario d'utilisation de bout en bout

1. **Importer le workflow :**

   ```bash
   curl -X POST http://localhost:8080/admin/workflows/import/onboard \
     -H "Content-Type: application/json" \
     -d @onboard-workflow.json
   ```

2. **Démarrer une instance** (pour un utilisateur "alice") :

   ```bash
   curl -X POST "http://localhost:8080/workflows/instance?workflowName=onboard&username=alice&startActionId=start"
   ```

   - Notez l'`id` retourné : il vous servira à la suite.

3. **Obtenir les prochaines actions disponibles :**

   ```bash
   curl http://localhost:8080/workflows/instance/{ID}/available
   ```

4. **Déclencher une action humaine, par ex. "fillUserInfo" :**

   ```bash
   curl -X POST http://localhost:8080/workflows/instance/{ID}/action/fillUserInfo \
     -H "Content-Type: application/json" \
     -d '{"email":"bob@example.com"}'
   ```
   - Si les conditions sont validées, l'action automatique "validateEmail" sera exécutée derrière, et la prochaine action humaine proposée sera "approveManager".

5. **Enchainer, etc :**
   - À chaque étape, interrogez `/available` pour décider du GUI à afficher.

---

## Remarques techniques

- Les conditions sont évaluées en MVEL, sur le contexte d'exécution courant.
- Toute l’édition du “processus” se fait via le JSON (import/export).
- La logique de chaque action automatique (par ex : “valider email”) est à implémenter dans un bean Java CDI, mappé par convention sur son `actionId`.
- Le contexte d’exécution est modifiable/dynamique, totalement restitué dans chaque retour `/available` ou d’action.
- La validation des paramètres d’action humaine (type, pattern, obligatoire) est automatique côté backend.

---

## Sécurité & Bonnes pratiques

- L’exécution d’une action n’est possible par API que si l’action est bien dans les actions suivantes permises (validation sécurisée).
- Les transitions ne s’effectuent que si les conditions MVEL sont satisfaites.
- L’ensemble du cheminement (humaine + autos) s’effectue dans la même transaction pour assurer la cohérence.

---

## Contribution & évolution

- Ajoutez de nouveaux types de paramètres ou de conditions dans vos configurations JSON.
- Le code Java CDI de vos actions métiers peut injecter n’importe quel autre service (mail, BDD, API…).
- Pour ajouter un nouveau “type” d’action automatique, créez simplement une classe `@ApplicationScoped` qui implémente l’interface WorkflowAction avec pour nom Class `ActionIdAction`.

---

## Test rapide

Créez un fichier `onboard-workflow.json` contenant l’exemple ci-dessus, puis :

```sh
curl -X POST http://localhost:8080/admin/workflows/import/onboard \
  -H "Content-Type: application/json" \
  -d @onboard-workflow.json
```

---

Pour toute contribution ou documentation enrichie, ouvrez une issue ou un MR !