package com.picpose.bestphotographyapp.data.repository

import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.Category
import com.picpose.bestphotographyapp.data.models.Post
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class HomeRepository {
    private val apiService = RetrofitClient.apiService
    private val apiKey = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c"

    suspend fun getFeaturedPosts(): Flow<Result<List<Post>>> = flow {
        try {
            val response = apiService.getFeaturedPosts(apiKey, 5)
            if (response.isSuccessful && response.body() != null) {
                emit(Result.success(response.body()!!.data))
            } else {
                emit(Result.failure(Exception("Failed to load featured posts")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun getRecentPosts(): Flow<Result<List<Post>>> = flow {
        try {
            val response = apiService.getPosts(apiKey, 10, 0)
            if (response.isSuccessful && response.body() != null) {
                emit(Result.success(response.body()!!.data))
            } else {
                emit(Result.failure(Exception("Failed to load recent posts")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun getCategories(): Flow<Result<List<Category>>> = flow {
        try {
            val response = apiService.getCategories(apiKey)
            if (response.isSuccessful && response.body() != null) {
                emit(Result.success(response.body()!!.data))
            } else {
                emit(Result.failure(Exception("Failed to load categories")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // Mock data as fallback for offline mode
    fun getMockFeaturedPosts(): List<Post> = listOf(
        Post(
            id = "1",
            title = "Golden Hour Portrait Mastery",
            description = "Learn the secrets of capturing stunning portraits during golden hour with professional lighting techniques.",
            image = "https://scontent.flko10-1.fna.fbcdn.net/v/t39.30808-6/539403726_759525540326780_2460644145648515262_n.jpg?stp=dst-jpg_s640x640_tt6&_nc_cat=111&ccb=1-7&_nc_sid=127cfc&_nc_ohc=ufMYi0olijsQ7kNvwGQ6Pw9&_nc_oc=AdlPTyvGHEuNsCe6nAdZWheqZ5HLCijalnClIGnZcpDoyPAQsCxRK8mfeiz2xd2yyWM-ZjwS3JRttL6egzggnWAo&_nc_zt=23&_nc_ht=scontent.flko10-1.fna&_nc_gid=fTqMTkUhnRwXytlUEWMERw&oh=00_AfYomHoshWTqYHTnbCtpt8PIb8cUXKXoDAeOWGz0tR7eQw&oe=68BF1A34",
            category = "Portrait",
            author = "Sarah Johnson",
            created_at = "2024-01-15T10:30:00Z",
            likes = 324,
            views = 2150,
            is_featured = true
        ),
        Post(
            id = "2",
            title = "Street Photography Secrets",
            description = "Master the art of candid street photography with these professional tips and techniques.",
            image = "https://images.unsplash.com/photo-1449824913935-59a10b8d2000?w=800",
            category = "Street",
            author = "Mike Chen",
            created_at = "2024-01-14T15:45:00Z",
            likes = 198,
            views = 1820,
            is_featured = true
        ),
        Post(
            id = "3",
            title = "Landscape Composition Rules",
            description = "Discover the fundamental rules of landscape composition that will transform your nature photography.",
            image = "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800",
            category = "Landscape",
            author = "Emma Wilson",
            created_at = "2024-01-13T08:20:00Z",
            likes = 456,
            views = 3240,
            is_featured = true
        )
    )

    fun getMockCategories(): List<Category> = listOf(
        Category("1", "Portrait", "People & Portrait Photography", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400", 45),
        Category("2", "Landscape", "Nature & Scenic Photography", "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400", 32),
        Category("3", "Street", "Urban & Street Photography", "https://images.unsplash.com/photo-1449824913935-59a10b8d2000?w=400", 28),
        Category("4", "Wildlife", "Animals & Wildlife Photography", "https://images.unsplash.com/photo-1564349683136-77e08dba1ef7?w=400", 19),
        Category("5", "Architecture", "Buildings & Architecture", "https://images.unsplash.com/photo-1487958449943-2429e8be8625?w=400", 24)
    )

    fun getMockAIPrompts(): List<AIPrompt> = listOf(
        AIPrompt(
            id = "1",
            title = "Cinematic Portrait Style",
            shortPrompt = "A hyper-detailed 3D render of a man in casual clothing (dark hoodie, jeans, sneakers) standing confidently on a vibrant, toy-themed pedestal surrounded by iconic cartoon characters.",
            fullPrompt = "A hyper-detailed 3D render of a man in casual clothing (dark hoodie, jeans, sneakers) standing confidently on a vibrant, toy-themed pedestal surrounded by iconic cartoon characters. The pedestal is whimsical and dynamic, constructed with toy props, barrels, and colorful elements. Famous cartoon figures like Dexter from Dexter’s Laboratory, the Powerpuff Girls (Blossom, Bubbles, Buttercup), Johnny Bravo, Scooby-Doo with Shaggy, Tom and Jerry, Popeye, and Fred & Barney from The Flintstones are positioned around him, interacting playfully. The style is ultra-realistic with exaggerated toy-like textures, rich colors, cinematic lighting, and a collectible action-figure vibe. The atmosphere is nostalgic, fun, and imaginative, blending childhood cartoon worlds with modern figure artistry. 8K Resolution create image.",
            imageUrl = "https://scontent.flko9-2.fna.fbcdn.net/v/t39.30808-6/540320299_760341930245141_5862826206789549255_n.jpg?stp=dst-jpg_p526x296_tt6&_nc_cat=109&ccb=1-7&_nc_sid=833d8c&_nc_ohc=yGvSPwRZz2cQ7kNvwExPq6R&_nc_oc=AdnGnDs81lTwN0dF1-MpY7C6MEz2MntD15wz74e_4lEFJK7hSbiDJSxLSkAB1iLG70UPG0A5Il9qnTJ7BN4ySW_r&_nc_zt=23&_nc_ht=scontent.flko9-2.fna&_nc_gid=qnuQ2gmu4aAwlOR9FStcTg&oh=00_AfZXOAQT7glNscLQwwmk9nIRU3uAnhVHCg9nAhd4rPUwrQ&oe=68BF0A97",
            category = "Portrait",
            likes = 234,
            isPopular = true
        ),
        AIPrompt(
            id = "2",
            title = "Professional Studio Setup",
            shortPrompt = "Professional studio photography with cinematic lighting and modern backdrop.",
            fullPrompt = "Professional studio photography setup with two subjects in a modern film studio. Cinematic lighting with softbox lights, professional camera equipment visible in background.",
            imageUrl = "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&h=400&fit=crop",
            category = "Studio",
            likes = 189,
            isPopular = true
        ),
        AIPrompt(
            id = "3",
            title = "Cyberpunk Night Scene",
            shortPrompt = "Futuristic cyberpunk style with neon city lights and dark atmospheric mood.",
            fullPrompt = "Ultra-realistic 8K portrait of a mysterious woman with wet hair, holding a single red rose between her lips. Her face and chest are covered with delicate water droplets, glistening under soft cinematic lighting. The atmosphere is dark and dramatic, with falling rose petals surrounding her. Sharp focus on her piercing eyes and vivid red rose, with a deep contrast between her pale skin and black outfit. Moody, sensual, and cinematic photography style, hyper-detailed textures, ultra-sharp clarity, 8K resolution.",
            imageUrl = "https://scontent.flko10-1.fna.fbcdn.net/v/t39.30808-6/539403726_759525540326780_2460644145648515262_n.jpg?stp=dst-jpg_s640x640_tt6&_nc_cat=111&ccb=1-7&_nc_sid=127cfc&_nc_ohc=ufMYi0olijsQ7kNvwGQ6Pw9&_nc_oc=AdlPTyvGHEuNsCe6nAdZWheqZ5HLCijalnClIGnZcpDoyPAQsCxRK8mfeiz2xd2yyWM-ZjwS3JRttL6egzggnWAo&_nc_zt=23&_nc_ht=scontent.flko10-1.fna&_nc_gid=fTqMTkUhnRwXytlUEWMERw&oh=00_AfYomHoshWTqYHTnbCtpt8PIb8cUXKXoDAeOWGz0tR7eQw&oe=68BF1A34",
            category = "Cyberpunk",
            tags = listOf("cyberpunk", "neon", "futuristic", "night"),
            likes = 156,
            isPopular = false
        ),
        AIPrompt(
            id = "4",
            title = "Golden Hour Fashion Photography",
            shortPrompt = "Fashion photography during golden hour with warm lighting and elegant pose.",
            fullPrompt = "High-fashion portrait photography during golden hour. Subject wearing elegant business attire, photographed with warm, soft natural lighting. Professional fashion photography style with shallow depth of field, creating beautiful bokeh in the background. Warm golden tones, sophisticated styling, and confident pose. Shot with professional camera equipment, perfect lighting conditions, and high-end fashion photography techniques.",
            imageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=600",
            category = "Fashion",
            tags = listOf("fashion", "golden hour", "portrait", "elegant"),
            likes = 298,
            isPopular = true
        ),
        AIPrompt(
            id = "5",
            title = "Minimalist Studio Portrait",
            shortPrompt = "Clean minimalist studio portrait with professional lighting and simple background.",
            fullPrompt = "Minimalist studio portrait with clean, simple background. Professional lighting setup creating soft, even illumination. Subject positioned with confident posture, wearing modern business attire. Clean aesthetic with neutral colors, professional photography equipment, and studio-quality lighting. Emphasizes simplicity, elegance, and professional presentation with high-quality camera work and perfect exposure.",
            imageUrl = "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=600",
            category = "Minimal",
            tags = listOf("minimal", "studio", "clean", "professional"),
            likes = 167,
            isPopular = false
        )
    )

}
