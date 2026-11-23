            if(!organizer.isEmpty()){
                TextView org=txt("Organizador: "+organizer,11,Color.LTGRAY);
                org.setPadding(dp(8),0,dp(8),dp(6));
                section.addView(org);
            }
            java.util.LinkedHashSet<String> dates=new java.util.LinkedHashSet<>();
            for(org.json.JSONObject fixture:entry.getValue()){
                String date=fixture.optString("when","").trim();
                if(!date.isEmpty())dates.add(date);
            }
            StringBuilder meta=new StringBuilder();
            if(!organizer.isEmpty())meta.append("Organizador: ").append(organizer);
            if(!dates.isEmpty()){
                if(meta.length()>0)meta.append(" · ");
                for(String date:dates){
                    if(meta.indexOf(" · ")>=0 || (!organizer.isEmpty() && !date.equals(dates.iterator().next())))meta.append(", ");
                    meta.append(date);
                }
            }
            if(meta.length()>0){
                TextView org=txt(meta.toString(),11,Color.LTGRAY);
                org.setPadding(dp(8),0,dp(8),dp(6));
                section.addView(org);
            }
            for(org.json.JSONObject match:entry.getValue()){
                number++;
                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(10),dp(8),dp(10),dp(8));
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
                params.bottomMargin=dp(3);
                section.addView(card,params);
                card.setBackgroundColor(panel);
                String opponent=match.optString("opponent","Rival pendiente");
                card.addView(txt(number+". "+(match.optBoolean("home")?"Local · ":"Visitante · ")+opponent,12,Color.WHITE));
                String when=match.optString("when","").trim();
                String detail="Puntos posibles: 0–5";
                card.addView(txt(detail,11,accent));
            }
        }
        header.setOnClickListener(v->{
            boolean open=content.getVisibility()!=View.VISIBLE;
            content.setVisibility(open?View.VISIBLE:View.GONE);
            header.setText((open?"▾ ":"▸ ")+"Partidos pendientes ("+matches.length()+")");
        });
    }

}
