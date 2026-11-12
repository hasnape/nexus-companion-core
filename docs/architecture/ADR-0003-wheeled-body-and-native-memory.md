# Corps roulant, mémoire et dialogue natif

Statut : couche de planification et simulation implémentée. Le contrôleur matériel reste à réaliser selon ADR-0001.

## Corps roulant

Le corps est un tricycle : deux roues latérales fixes de traction et une roue centrale directrice. La direction se configure à l’avant ou à l’arrière. Les jambes ne font pas partie de cette base.

`packages/shared/src/wheeled-body.ts` possède la géométrie, les limites et le calcul des consignes. Le profil virtuel utilise un empattement de 0,28 m, une voie de 0,26 m et un rayon de roue de 0,05 m. Ces valeurs servent à la simulation ; le futur châssis devra fournir ses mesures réelles.

Le repère est placé au milieu de l’essieu de traction : x vers l’avant, y vers la gauche, lacet positif vers la gauche. Pour une vitesse longitudinale v et une vitesse de lacet w, le braquage avant vaut `atan(w * empattement / v)`. Le braquage arrière prend le signe opposé. Les vitesses des roues latérales valent `(v - w * voie / 2) / rayon` et `(v + w * voie / 2) / rayon`, en rad/s. Le tricycle ne pivote pas sur place.

| Donnée | Unité / contrat |
| --- | --- |
| Vitesse longitudinale | m/s |
| Vitesse de lacet | rad/s |
| Braquage central | rad |
| Vitesses des roues gauche/droite | rad/s |
| Durée d’une demande | 1 à 5 000 ms |
| Position simulée | m et rad |

Le plan réutilise `NexusMotionRequest` du protocole existant. Il produit des échantillons de trajectoire espacés au maximum de 100 ms. Android les interpole pour afficher le déplacement et les roues ; la géométrie n’est pas recopiée dans le rendu. Les commandes utilisateur directes sont la seule entrée. Ni une note, ni un article lu, ni une réponse prononcée ne deviennent une commande du corps.

Le plan est explicitement marqué `execution: simulation`. Le transport vers des moteurs est absent. `robot.motion` reste désactivé dans la simulation de dock existante. Le futur adaptateur devra négocier ses capacités, authentifier les enveloppes, refuser les séquences périmées et appliquer les limites mécaniques, les capteurs, l’arrêt physique et le watchdog local. Une commande vocale d’arrêt ne remplace pas cet arrêt matériel.

Le bouton Arrêter fonctionne pendant un calcul. L’arrêt invalide aussi les plans en attente du worker. Le mode robot s’arrête à la sortie, à l’arrière-plan, à la perte de session vocale ou à l’échéance. Une recréation de l’écran conserve la pose et la configuration, sans reprendre un mouvement. Le mot « stop » sans activation n’est accepté que pendant un mouvement virtuel et sur un résultat final du micro.

Référence primaire de nomenclature : [ROS 2 tricycle steering controller](https://control.ros.org/rolling/doc/ros2_controllers/tricycle_steering_controller/doc/userdoc.html). L’application n’ajoute pas de dépendance ROS.

## Mémoire et compression

Le format des notes reste JSON schema 1, éventuellement GZIP, dans l’enveloppe AES-GCM existante. Au-delà de 4 Kio, l’écriture compare GZIP standard et compression forte et conserve le plus petit résultat, avec retour au JSON si nécessaire. Les textes, les identifiants, les dates et les sources restent intacts. Aucun changement de clé ni de schéma n’est nécessaire.

Le cache garde les enregistrements validés et les octets chiffrés correspondants. Chaque lecture compare les octets du fichier atomique ; un horodatage identique ne peut pas masquer une écriture externe. Des octets différents provoquent une nouvelle authentification, décompression et validation. Un fichier altéré n’est ni remplacé par le cache ni réinitialisé.

Les doublons de casse, d’espacement et de ponctuation finale réutilisent la note originale. La provenance et l’expiration restent des critères distincts. « État de ta mémoire » indique le nombre de notes, l’espace chiffré utilisé et l’économie de compression avant chiffrement.

## Questions et entrée vocale

Le même analyseur reconnaît les questions polies et sans ponctuation pour la détection de mémoire et la formulation. Une question privée ne devient pas une proposition d’enregistrement. Les questions de lieu, de raison et de date demandent une preuve du bon type. La sélection distingue le membre de la famille et l’attribut demandé.

La capture PCM passe de 200 à 100 ms par bloc. Une fin de capture sans résultat partiel interroge le résultat final du décodeur. Le décodage de consensus Vosk est conservé, avec les confiances par mot. Le parseur accepte aussi le format n-best en conservant ses vraisemblances dans un champ séparé ; ce ne sont pas des probabilités. Une confirmation courte de confiance inférieure à 0,45 est redemandée, sans exécuter l’accord.

Référence primaire des formats : [Vosk Recognizer](https://github.com/alphacep/vosk-api/blob/master/src/recognizer.cc). La précision acoustique reste à mesurer sur téléphone ; les changements ne prétendent pas remplacer le modèle français.
