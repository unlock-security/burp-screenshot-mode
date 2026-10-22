package it.unlocksecurity.burp.screenshotmode.render.pretty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JavaScriptFormatterTest {

    @Test
    void formatsMinifiedCode() {
        assertEquals(
            """
            !function(e){
              "use strict";
              var n={
                a:1,
                b:[1,2],
                f:function(x){
                  return x/2
                }
              };
              if(e){
                for(var i=0;i<3;i++){
                  t(i)
                }
              }else{
                throw new Error("x")
              }
              try{
                x()
              }catch(o){}finally{
                y()
              }
              e.on("x",()=>{
                return{
                  ok:!0
                }
              })
            }(window);""",
            JavaScriptFormatter.format(
                "!function(e){\"use strict\";var n={a:1,b:[1,2],f:function(x){return x/2}};if(e){for(var i=0;i<3;i++)" +
                    "{t(i)}}else{throw new Error(\"x\")}try{x()}catch(o){}finally{y()}e.on(\"x\",()=>{return{ok:!0}})}(window);"
            )
        );
    }

    @Test
    void putsElementsOfArraysOfObjectsOnTheirOwnLines() {
        assertEquals(
            """
            [
              {
                "phrase":"http server gave http response to https"
              },
              {
                "phrase":"http failure response for https"
              }
            ]""",
            JavaScriptFormatter.format(
                "[{\"phrase\":\"http server gave http response to https\"},{\"phrase\":\"http failure response for https\"}]"
            )
        );
        assertEquals("m=[\n  [1,2],\n  [3]\n];\nn=[1,[2]];", JavaScriptFormatter.format("m=[[1,2],[3]];n=[1,[2]];"));
    }

    @Test
    void tellsRegularExpressionsFromDivisions() {
        // Breaking after the ";" or "{" inside the regex would misrepresent the code.
        assertEquals(
            "a=b/c/d;\nr=/[;{]\\//g.test(s)/2;",
            JavaScriptFormatter.format("a=b/c/d;r=/[;{]\\//g.test(s)/2;")
        );
        assertEquals("return/;{/.test(x)", JavaScriptFormatter.format("return/;{/.test(x)"));
    }

    @Test
    void copiesStringsTemplatesAndCommentsAsTheyAre() {
        assertEquals(
            """
            s='a;{}';
            t=`x${y.map(z=>{return`;${z}`})};{}`;/* a;{} */
            u=1; // b;{}
            v=2;""",
            JavaScriptFormatter.format("s='a;{}';t=`x${y.map(z=>{return`;${z}`})};{}`;/* a;{} */\nu=1; // b;{}\nv=2;")
        );
    }

    @Test
    void keepsSourceLineBreaksAndReindents() {
        assertEquals(
            """
            function f(a,
              b) {

              return a
              + b;
            }
            const o = {
              x: [
                1,
                2
              ]
            };""",
            JavaScriptFormatter.format(
                "function f(a,\n        b) {\n\n\n    return a\n        + b;\n}\nconst o = {\n  x: [\n    1,\n    2\n  ]\n};\n"
            )
        );
    }
}
