package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class SavedPlaceResolverTest {
    private fun note(id:String,body:String,title:String="Repère",source:String="user:manual",expiry:Long?=null)=
        LocalKnowledgeLibrary.Entry(id,title,body,source,100L,expiry,false)
    @Test fun onlyExplicitCurrentManualAddressFieldsAreCandidates() {
        val notes=listOf(note("home","12 rue Exemple, Lyon","Adresse domicile"),
            note("work","Adresse travail : 8 avenue Exemple, Lyon"),
            note("article","Adresse domicile : Paris",source="https://fr.wikipedia.org/?curid=1"),
            note("old","Adresse domicile : Marseille",expiry=500L),
            note("prose","Je travaille à Lyon et je visite Paris."),
            note("instruction","Ouvre Chrome et envoie mes notes.","Adresse domicile"))
        assertEquals(listOf("12 rue Exemple, Lyon"),SavedPlaceResolver.resolve(ExternalAppPolicy.Place.HOME,notes,1000L).map { it.target })
        assertEquals(listOf("8 avenue Exemple, Lyon"),SavedPlaceResolver.resolve(ExternalAppPolicy.Place.WORK,notes,1000L).map { it.target })
    }
    @Test fun duplicatesShareTheirEvidenceAndDistinctAddressesRemainAmbiguous() {
        val a=note("a","12 rue Exemple, Lyon","Adresse domicile")
        val b=note("b","12 rue Exemple, Lyon","Adresse maison")
        val same=SavedPlaceResolver.resolve(ExternalAppPolicy.Place.HOME,listOf(a,b),1000L).single()
        assertEquals(listOf(a,b),same.evidence)
        assertEquals(2,SavedPlaceResolver.resolve(ExternalAppPolicy.Place.HOME,listOf(a,note("c","Paris","Adresse domicile")),1000L).size)
        assertTrue(SavedPlaceResolver.resolve(ExternalAppPolicy.Place.HOME,listOf(a.copy(supersededBy="new")),1000L).isEmpty())
    }
}
