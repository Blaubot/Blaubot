---
layout: page
title: News
noToc: true
---
<ul class="post-list">
    {% for post in site.posts %}
        <li>
            <span class="post-meta">{{ post.date | date: "%b %-d, %Y" }}</span>

            <a class="post-link" href="{{ post.url | prepend: site.baseurl }}">{{ post.title }}</a>
        </li>
        {{ post.excerpt }}
        {{pretty}}
    {% endfor %}
</ul>