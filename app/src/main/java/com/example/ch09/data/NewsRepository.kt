package com.example.ch09.data

import com.example.ch09.data.local.ArticleDao
import com.example.ch09.data.mapper.toDomain
import com.example.ch09.data.mapper.toEntity
import com.example.ch09.data.model.Article
import com.example.ch09.data.model.NewsCategory
import com.example.ch09.data.model.NewsFeed
import com.example.ch09.data.model.OfflineInfo
import com.example.ch09.data.remote.NewsApiService
import com.example.ch09.data.remote.dto.NewsResponseDto
import kotlin.coroutines.cancellation.CancellationException

interface NewsRepository {
    suspend fun getTopHeadlines(category: NewsCategory): Result<NewsFeed>
    suspend fun searchNews(query: String): Result<NewsFeed>
    suspend fun getArticle(url: String): Article?
}

class NewsRepositoryImpl(
    private val api: NewsApiService,
    private val dao: ArticleDao?,
    private val clock: () -> Long = System::currentTimeMillis
) : NewsRepository {

    override suspend fun getTopHeadlines(category: NewsCategory): Result<NewsFeed> =
        fetch(cacheKey = category.apiValue, remote = { api.getTopHeadlines(category = category.apiValue) })

    override suspend fun searchNews(query: String): Result<NewsFeed> =
        fetch(
            cacheKey = "__search__",
            remote = { api.searchNews(query = query) },
            fallbackSearch = {
                dao?.searchCached(query)?.map { it.toDomain() }?.distinctBy { it.url } ?: emptyList()
            }
        )

    override suspend fun getArticle(url: String): Article? {
        return dao?.getArticleByUrl(url)?.toDomain()
    }

    private suspend fun fetch(
        cacheKey: String,
        remote: suspend () -> NewsResponseDto,
        fallbackSearch: suspend () -> List<Article>? = { null }
    ): Result<NewsFeed> {
        val result = runCatching {
            remote().articles
                .mapNotNull { it.toDomain() }
                .distinctBy { it.url }
        }

        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }

        if (result.isSuccess) {
            val articles = result.getOrThrow()
            val now = clock()
            val entities = articles.mapIndexed { index, article ->
                article.toEntity(category = cacheKey, position = index, cachedAt = now)
            }
            dao?.replaceCategory(cacheKey, entities)
            return Result.success(NewsFeed(articles))
        } else {
            val error = result.exceptionOrNull()!!
            val cachedEntities = if (cacheKey == "__search__") {
                fallbackSearch()
            } else {
                dao?.getArticles(cacheKey)?.map { it.toDomain() }
            }

            if (!cachedEntities.isNullOrEmpty()) {
                val cachedAt = dao?.getArticles(cacheKey)?.firstOrNull()?.cachedAt ?: clock()
                return Result.success(
                    NewsFeed(
                        articles = cachedEntities,
                        offline = OfflineInfo(
                            cachedAt = cachedAt,
                            reason = error.toUserMessage()
                        )
                    )
                )
            }
            return Result.failure(error)
        }
    }
}