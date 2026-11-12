package fr.nexuscompanion.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class WikipediaAnswerPolicyTest {
    private fun page(id:Int,title:String,extract:String,index:Int)=JSONObject().put("pageid",id).put("title",title).put("extract",extract).put("index",index)
    private fun response(vararg pages:JSONObject)=JSONObject().put("query",JSONObject().put("pages",JSONArray(pages.toList())))
    @Test fun aFilmSharingTheWordSalutCannotAnswerARequestAboutGreetings() {
        val bad=page(1,"Notre salut","Notre salut est un film dramatique réalisé par un cinéaste.",1)
        val good=page(2,"Formules de salutation et d’adieu","Les formules de salutation permettent de dire bonjour avec politesse.",2)
        assertEquals(2,WikipediaKnowledgeSearch.readArticle(response(bad,good),"les réponses de salutations")?.pageId)
        assertNull(WikipediaKnowledgeSearch.readArticle(response(bad),"les réponses de salutations"))
    }
    @Test fun unrelatedOrDisambiguationResultsDoNotPreventSelectingALaterRelevantPage() {
        val disambiguation=page(1,"Volcan","Volcan désigne plusieurs choses.",1).put("pageprops",JSONObject().put("disambiguation",""))
        val unrelated=page(2,"Vélo","Un véhicule à deux roues.",2)
        val good=page(3,"Volcan","Un volcan émet de la lave.",3)
        assertEquals(3,WikipediaKnowledgeSearch.readArticle(response(disambiguation,unrelated,good),"les volcans")?.pageId)
        assertNull(WikipediaKnowledgeSearch.readArticle(response(unrelated),"les volcans"))
    }
    @Test fun factualMentionsOfSeriesAreNotMistakenForTvShows() {
        assertTrue(WikipediaAnswerPolicy.relevance("Renault","Renault","Renault est un constructeur automobile qui fabrique des voitures de série.")>0)
    }
    @Test fun aHowManyQuestionSelectsTheOriginalNumericSentenceInsteadOfReadingTheWholeIntroduction() {
        val text="Le département est une division territoriale. Son chef-lieu est le siège d’une préfecture. Il existe 101 départements français."
        val result=WikipediaAnswerPolicy.excerpt("combien de départements français",text)
        assertEquals("Il existe 101 départements français.",result)
        assertFalse(result.contains("chef-lieu"))
    }
    @Test fun extractsStayBoundedWithoutSplittingUnicodePairsOrChangingStoredEvidence() {
        val text="Le volcan "+"émet de la lave 😀 ".repeat(100)
        val result=WikipediaAnswerPolicy.excerpt("volcan",text)
        assertTrue(result.length<=650)
        assertFalse(result.dropLast(1).last().isHighSurrogate())
        assertTrue(text.length>result.length)
    }
}
