# zookeeper-curator-tests

Ce projet est une application Java pour tester les fonctionnalités d'Apache ZooKeeper et Curator. 

## Structure du projet

```
zookeeper-curator-tests
├── src
│   ├── main
│   │   ├── java
│   │   │   └── dev
│   │   │       └── boissin
│   │   │           └── App.java
│   │   └── resources
│   │       └── application.properties
│   ├── test
│       ├── java
│       │   └── dev
│       │       └── boissin
│       │           └── AppTest.java
│       └── resources
├── pom.xml
└── README.md
```

## Prérequis

- Java 21 ou supérieur
- Maven

## Installation

1. Clonez le dépôt :
   ```
   git clone <url-du-depot>
   cd zookeeper-curator-tests
   ```

2. Installez les dépendances avec Maven :
   ```
   mvn install
   ```

## Lancer avec docker

```
docker-compose up -d
```


## Auteurs

- Damien BOISSIN - Développeur principal

## License

Ce projet est sous licence AGPL.
