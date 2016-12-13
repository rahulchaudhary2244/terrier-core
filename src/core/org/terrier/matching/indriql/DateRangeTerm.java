package org.terrier.matching.indriql;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terrier.matching.MatchingQueryTerms.QueryTermProperties;
import org.terrier.matching.models.WeightingModel;
import org.terrier.structures.BitIndexPointer;
import org.terrier.structures.CollectionStatistics;
import org.terrier.structures.EntryStatistics;
import org.terrier.structures.Index;
import org.terrier.structures.IndexUtil;
import org.terrier.structures.Lexicon;
import org.terrier.structures.LexiconEntry;
import org.terrier.structures.Pointer;
import org.terrier.structures.PostingIndex;
import org.terrier.structures.postings.IterablePosting;
import org.terrier.structures.postings.ORIterablePosting;

public class DateRangeTerm extends QueryTerm {

	public static final String STRING_PREFIX = "#datebetween";
	
	protected static final Logger logger = LoggerFactory.getLogger(MultiQueryTerm.class);
	private static final long serialVersionUID = 1L;
	Date lowRange;
	Date hiRange;
	
	public DateRangeTerm(Date _lo, Date _hi)
	{
		this.lowRange = _lo;
		this.hiRange = _hi;
		assert (_lo != null) && (_hi != null);//currently we need both
	}

	@Override
	public String toString() {
		return STRING_PREFIX + "("+lowRange + " " + hiRange+ ")";
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	Pair<EntryStatistics, IterablePosting> getPostingIterator(Index index)
			throws IOException {
		Lexicon<Date> lexDate = (Lexicon<Date>) index.getIndexStructure("datelexicon");
		PostingIndex<Pointer> invDate = (PostingIndex<Pointer>) index.getIndexStructure("dateinverted");
		List<LexiconEntry> _le = new ArrayList<LexiconEntry>();
		List<IterablePosting> _joinedPostings = new ArrayList<IterablePosting>();
		
		EntryStatistics entryStats;
		for(Iterator<Map.Entry<Date,LexiconEntry>> iter = lexDate.getLexiconEntryRange(lowRange, hiRange);iter.hasNext();)
		{
			Map.Entry<Date,LexiconEntry> lee = iter.next();
			String alternative = lee.getKey().toString();
			LexiconEntry t = lee.getValue();
			if (t == null) {
				logger.debug("Component term Not Found: " + alternative);
			} else {
				_le.add(t);
				_joinedPostings.add(invDate.getPostings((BitIndexPointer) t));
			}
		}
		if (_le.size() == 0)
		{
			//TODO consider if we should return an empty posting list iterator instead
			logger.warn("No alternatives matched in "+this.toString());
			return null;
		}
		entryStats = MultiQueryTerm.mergeStatistics(_le.toArray(new LexiconEntry[_le.size()]));
		return Pair.of(entryStats, (IterablePosting) ORIterablePosting.mergePostings(_joinedPostings.toArray(new IterablePosting[_joinedPostings.size()])));
	}

	
	@Override
	public MatchingEntry getMatcher(QueryTermProperties qtp, Index index,
			Lexicon<String> lexTerm, PostingIndex<Pointer> invTerm,
			CollectionStatistics collectionStats) throws IOException {
		
		
		
		
		
		WeightingModel[] wmodels = qtp.termModels.toArray(new WeightingModel[0]);
		if (wmodels.length == 0) {
			logger.warn("No weighting models for multi-term query group "+toString()+" , skipping scoring");
			return null;
		}
		EntryStatistics entryStats = qtp.stats;
		
		Pair<EntryStatistics,IterablePosting> pair = this.getPostingIterator(index);
		
		if (entryStats == null)
			entryStats = pair.getKey();
		if (logger.isDebugEnabled())
			logger.debug("Date range term "+this.toString()+ " stats" + entryStats.toString());
		for (WeightingModel w : wmodels)
		{
			w.setEntryStatistics(entryStats);
			w.setKeyFrequency(qtp.weight);
			w.setCollectionStatistics(collectionStats);
			IndexUtil.configure(index, w);
			w.prepare();			
		}
		
		boolean required = false;
		if (qtp.required != null && qtp.required)
			required = true;
		return new MatchingEntry(pair.getRight(), entryStats, qtp.weight, wmodels, required);
	}

}
