package com.twock.geneaology.test;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import com.mattharrah.gedcom4j.model.*;
import com.mattharrah.gedcom4j.parser.GedcomParser;
import com.mattharrah.gedcom4j.parser.GedcomParserException;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * @author Chris Pearson
 */
public class TestGedComFileParser {
  private static final Logger log = LoggerFactory.getLogger(TestGedComFileParser.class);
  public static final LocalDate TODAY = new LocalDate();

  @Test
  public void testFileParse() throws IOException, GedcomParserException {
    ZipInputStream inputStream = new ZipInputStream(getClass().getResourceAsStream("/Archdale_2012-10-22.zip"));
    inputStream.getNextEntry();
    GedcomParser gp = new GedcomParser();
    gp.load(inputStream);

    // parse all the birth dates we have access to
    final TreeMap<String, LocalDate> birthDates = new TreeMap<String, LocalDate>();
    for(Individual individual : gp.gedcom.individuals.values()) {
      LocalDate birthDate = getBirthDate(individual);
      if(birthDate != null) {
        birthDates.put(individual.xref, birthDate);
      }
    }
    log.info("Parsed {} birth dates", birthDates.size());

    // find all the families without ancestors
    List<Family> trunkFamilies = new ArrayList<Family>();
    for(Family family : gp.gedcom.families.values()) {
      if((family.wife == null || family.wife.getAncestors().isEmpty()) && (family.husband == null || family.husband.getAncestors().isEmpty())) {
        trunkFamilies.add(family);
      }
    }
    log.info("Found {} families without known ancestors", trunkFamilies.size());

    // remove all the families where either husband or wife doesn't have a birth date
    List<Family> noBirthDates = new ArrayList<Family>();
    for(Iterator<Family> iterator = noBirthDates.iterator(); iterator.hasNext(); ) {
      Family next = iterator.next();
      if((next.wife == null || !birthDates.containsKey(next.wife.xref)) && (next.husband == null || !birthDates.containsKey(next.husband.xref))) {
        noBirthDates.add(next);
        iterator.remove();
      }
    }
    log.info("Removed {} families without husband/wife birth dates, leaving {} families without known ancestors, but known birth dates", noBirthDates.size(), trunkFamilies.size());

    Collections.sort(trunkFamilies, new Comparator<Family>() {
      @Override
      public int compare(Family o1, Family o2) {
        return Integer.compare(daysAgo(o1, birthDates), daysAgo(o2, birthDates));
      }
    });


  }

  private int daysAgo(Family family, TreeMap<String, LocalDate> birthDates) {
    LocalDate wifeBorn = family.wife == null ? null : birthDates.get(family.wife.xref);
    LocalDate husbandBorn = family.husband == null ? null : birthDates.get(family.husband.xref);
    if(wifeBorn != null && husbandBorn != null) {
      if(wifeBorn.compareTo(husbandBorn) < 0) {
        return Days.daysBetween(husbandBorn, TODAY).getDays();
      } else {
        return Days.daysBetween(wifeBorn, TODAY).getDays();
      }
    } else if(husbandBorn != null) {
      return Days.daysBetween(husbandBorn, TODAY).getDays();
    } else {
      return Days.daysBetween(wifeBorn, TODAY).getDays();
    }
  }

  private static final DateTimeFormatter DATE_DD_MMM_YYYY = new DateTimeFormatterBuilder().appendDayOfMonth(2).appendLiteral(' ').appendMonthOfYearShortText().appendLiteral(' ').appendYear(4, 4).toFormatter();
  private static final DateTimeFormatter DATE_MMM_YYYY = new DateTimeFormatterBuilder().appendMonthOfYearShortText().appendLiteral(' ').appendYear(4, 4).toFormatter();
  private static final DateTimeFormatter DATE_YYYY = new DateTimeFormatterBuilder().appendYear(4, 4).toFormatter();
  private static final Pattern DATE_BETWEEN = Pattern.compile("BET (\\d{4}) AND (\\d{4})");
  private static final Pattern DATE_ABT = Pattern.compile("ABT (\\d{4})\\/(\\d{2})");

  private LocalDate getBirthDate(Individual individual) {
    for(IndividualEvent birth : individual.getEventsOfType(IndividualEventType.BIRTH)) {
      if(birth.date != null) {
        String toMatch = birth.date.replaceFirst("(AFT|BEF|ABT) ", "");
        try {
          return DATE_DD_MMM_YYYY.parseLocalDate(toMatch);
        } catch(Exception e) {
        }
        try {
          return DATE_MMM_YYYY.parseLocalDate(toMatch);
        } catch(Exception e) {
        }
        try {
          return DATE_YYYY.parseLocalDate(toMatch);
        } catch(Exception e) {
        }
        Matcher matcher;
        if((matcher = DATE_BETWEEN.matcher(birth.date)).matches()) {
          LocalDate d1 = new LocalDate(Integer.parseInt(matcher.group(1)), 1, 1);
          LocalDate d2 = new LocalDate(Integer.parseInt(matcher.group(2)), 1, 1);
          return d1.plusDays(Days.daysBetween(d1, d2).getDays() / 2);
        } else if((matcher = DATE_ABT.matcher(birth.date)).matches()) {
          int year1 = Integer.parseInt(matcher.group(1));
          LocalDate d1 = new LocalDate(year1, 1, 1);
          LocalDate d2 = new LocalDate(100 * (year1 / 100) + Integer.parseInt(matcher.group(2)), 1, 1);
          return d1.plusDays(Days.daysBetween(d1, d2).getDays() / 2);
        }
        log.warn("Unable to parse date {} for {}", birth.date, individual);
      }
    }
    return null;
  }
}
