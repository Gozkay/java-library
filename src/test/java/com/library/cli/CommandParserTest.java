package com.library.cli;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class CommandParserTest {
 @Test void quotedTitleIsOneArgument(){assertEquals(List.of("search","Clean Code"),new CommandParser().parse("search \"Clean Code\""));}
 @Test void unclosedQuoteIsRejected(){assertThrows(IllegalArgumentException.class,()->new CommandParser().parse("search \"Clean Code"));}
}
