package com.twock.geneaology;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import com.mattharrah.gedcom4j.model.*;
import com.mattharrah.gedcom4j.parser.GedcomParser;
import com.mattharrah.gedcom4j.parser.GedcomParserException;
import org.apache.commons.io.IOUtils;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chris Pearson
 */
public class CreateTree {
  private static final Logger log = LoggerFactory.getLogger(CreateTree.class);
  public static final LocalDate TODAY = new LocalDate();
  public static final double DAYS_PER_YEAR = 365.25;
  private static NumberFormat format = DecimalFormat.getNumberInstance();

  public static void main(String[] args) throws Exception {
    new CreateTree().parse();
  }

  public void parse() throws IOException, GedcomParserException {
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

/*
    List<Family> noBirthDates = new ArrayList<Family>();
    for(Iterator<Family> iterator = trunkFamilies.iterator(); iterator.hasNext(); ) {
      Family next = iterator.next();
      if((next.wife == null || !birthDates.containsKey(next.wife.xref)) && (next.husband == null || !birthDates.containsKey(next.husband.xref))) {
        noBirthDates.add(next);
        iterator.remove();
      }
    }
    log.info("Removed {} families without husband/wife birth dates, leaving {} families without known ancestors, but known birth dates", noBirthDates.size(), trunkFamilies.size());
*/

    Collections.sort(trunkFamilies, new Comparator<Family>() {
      @Override
      public int compare(Family o1, Family o2) {
        return Integer.compare(daysAgo(o2, birthDates), daysAgo(o1, birthDates));
      }
    });

    AtomicInteger counter = new AtomicInteger(1);
    StringBuilder sb = new StringBuilder();
    sb.append("function userdata() { fulltree = new midnode(\"");
    buildTree(trunkFamilies, sb, counter, birthDates);
    sb.append(";\"); }");
    File outputFile = new File("web/archdale.js");
    FileOutputStream out = null;
    try {
      out = new FileOutputStream(outputFile);
      IOUtils.write(sb.toString(), new BufferedOutputStream(out));
      log.info("Wrote {}", outputFile.getAbsolutePath());
    } finally {
      IOUtils.closeQuietly(out);
    }
  }

  private void buildTree(List<Family> trunkFamilies, StringBuilder sb, AtomicInteger counter, TreeMap<String, LocalDate> birthDates) {
    if(trunkFamilies.size() >= 2) {
      int splitPoint = trunkFamilies.size() / 2;
      sb.append('(');
      buildTree(trunkFamilies.subList(0, splitPoint), sb, counter, birthDates);
      sb.append(',');
      buildTree(trunkFamilies.subList(splitPoint, trunkFamilies.size()), sb, counter, birthDates);
      sb.append(')').append(counter.getAndIncrement()).append(":0");
    } else if(trunkFamilies.size() == 1) {
      Family family = trunkFamilies.get(0);
      String husbandText = getIndividualText(family.husband, birthDates);
      String wifeText = getIndividualText(family.wife, birthDates);
      if(!family.children.isEmpty()) {
        sb.append('(');
      }
      if(husbandText != null && wifeText != null) {
        sb.append('(').append(husbandText);
        sb.append(',').append(wifeText);
        sb.append(')').append(counter.getAndIncrement()).append(":0");
      } else if(husbandText != null) {
        sb.append(husbandText);
      } else {
        sb.append(wifeText);
      }
      if(!family.children.isEmpty()) {
        sb.append(',');
        List<Family> childsFamily = new ArrayList<Family>(family.children.size());
        for(Individual child : family.children) {
          for(FamilySpouse familySpouse : child.familiesWhereSpouse) {
            childsFamily.add(familySpouse.family);
          }
        }
        buildTree(childsFamily, sb, counter, birthDates);
        sb.append(')').append(counter.getAndIncrement()).append(":0");
      }
    }
  }

  private String getIndividualText(Individual individual, TreeMap<String, LocalDate> birthDates) {
    if(individual == null) {
      return null;
    } else {
      return individual.formattedName().replaceAll("/", "").replaceAll("[^A-Za-z_]", "_") + ":" + ago(birthDates.get(individual.xref));
    }
  }

  private String ago(LocalDate localDate) {
    return localDate == null ? "0" : format.format((double)Days.daysBetween(localDate, TODAY).getDays() / DAYS_PER_YEAR);
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
    } else if(wifeBorn != null) {
      return Days.daysBetween(wifeBorn, TODAY).getDays();
    } else {
      return 0;
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
